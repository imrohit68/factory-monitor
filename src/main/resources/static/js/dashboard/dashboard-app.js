(function () {
    'use strict';

    const appEl = document.getElementById('app');
    const errAttr = appEl ? appEl.getAttribute('data-modbus-error') : null;

    function parseAlertRepeatIntervalMinutes(el) {
        const v = el ? el.getAttribute('data-alert-repeat-interval-minutes') : null;
        if (v == null || v === '') return 15;
        const n = parseInt(v, 10);
        return Number.isFinite(n) && n > 0 ? n : 15;
    }

    function parseAlertMaxRepeats(el) {
        const v = el ? el.getAttribute('data-alert-max-repeats') : null;
        if (v == null || v === '') return 4;
        const n = parseInt(v, 10);
        return Number.isFinite(n) && n >= 0 ? n : 4;
    }

    const repeatIntervalMin = parseAlertRepeatIntervalMinutes(appEl);
    const boot = {
        modbusConnected: !!(appEl && appEl.getAttribute('data-modbus-connected') === 'true'),
        modbusError: errAttr || null,
        repeatEveryMs: repeatIntervalMin * 60 * 1000,
        maxAlertRepeats: parseAlertMaxRepeats(appEl)
    };

    const { createApp } = Vue;

    createApp({
        data() {
            return {
                workstations: [],
                playingKey: null,
                playlistIdx: 0,
                alertGapMs: 400,
                // From application.properties (system.dashboard-alert-*); repeating only while input stays active.
                repeatEveryMs: boot.repeatEveryMs,
                maxAlertRepeats: boot.maxAlertRepeats,
                modbusConnected: !!boot.modbusConnected,
                modbusError: boot.modbusError || null,
                matrixRows: [
                    {
                        rowIdx: 0,
                        smallLabel: 'ENGINEERS',
                        badgeLetter: 'E',
                        labelStack: false
                    },
                    {
                        rowIdx: 1,
                        smallLabel: 'LEADER',
                        badgeLetter: 'L',
                        labelStack: false
                    },
                    {
                        rowIdx: 2,
                        smallLabel: 'QUALITY',
                        smallLabelLine2: 'CONTROLLER',
                        badgeLetter: 'Q',
                        labelStack: true
                    }
                ],
                _currentPlaylist: [],
                _audioEl: null,
                _alertGapTimer: null,
                // Per-audio scheduling state (per active audio item key).
                _lastPlayedByKey: {},
                // Queue of due audios to play (no overlap; only one Audio element).
                _playQueue: [],
                _queuedKeys: {},
                // Snapshot of currently active audios by key (updated on each sync()).
                _activeAudioByKey: {},
                // Per-audio replay timers started after playback ends.
                _replayTimerByKey: {},
                // Number of repeats already queued/played per active key (resets on OFF->ON).
                _replayCountByKey: {},
                _pollInterval: null,
                _visibilityHandler: null,
                _beforeUnloadHandler: null,
                _gestureResumeAudio: null,
                /** Shown when alert audio could not start (browser autoplay policy). Cleared after first successful play(). */
                showAudioGestureHint: false,
                _refreshInFlight: false
            };
        },
        mounted() {
            this.refresh();
            this._pollInterval = setInterval(() => this.refresh(), 2000);
            
            this._visibilityHandler = () => {
                if (document.hidden) {
                    console.log('Page hidden event');
                    this.stopAlertAudio();
                } else {
                    console.log('Page visible event, syncing audio');
                    // Background tabs throttle timers; refresh immediately when user returns.
                    this.refresh();
                    this.syncAlertAudioPlaylist(this.workstations);
                }
            };
            document.addEventListener('visibilitychange', this._visibilityHandler);
            
            this._beforeUnloadHandler = () => {
                console.log('Page unload/pagehide event');
                this.stopAlertAudio();
            };
            window.addEventListener('beforeunload', this._beforeUnloadHandler);
            window.addEventListener('pagehide', this._beforeUnloadHandler);
            
            /**
             * Browsers block alert audio on first paint until a user gesture. Retrying sync after
             * pointer/touch/key fixes cold dashboard loads; navigating from admin works because a gesture occurred.
             */
            this._gestureResumeAudio = (e) => {
                if (document.hidden) return;
                if (e.type === 'keydown' && e.key !== 'Enter' && e.key !== ' ') return;
                this.syncAlertAudioPlaylist(this.workstations);
            };
            document.addEventListener('pointerdown', this._gestureResumeAudio, { capture: true, passive: true });
            document.addEventListener('keydown', this._gestureResumeAudio, { capture: true });
        },
        beforeUnmount() {
            console.log('Component unmounting');
            this.stopAlertAudio();
            if (this._pollInterval) {
                clearInterval(this._pollInterval);
                this._pollInterval = null;
            }
            if (this._visibilityHandler) {
                document.removeEventListener('visibilitychange', this._visibilityHandler);
                this._visibilityHandler = null;
            }
            if (this._beforeUnloadHandler) {
                window.removeEventListener('beforeunload', this._beforeUnloadHandler);
                window.removeEventListener('pagehide', this._beforeUnloadHandler);
                this._beforeUnloadHandler = null;
            }
            if (this._gestureResumeAudio) {
                document.removeEventListener('pointerdown', this._gestureResumeAudio, { capture: true });
                document.removeEventListener('keydown', this._gestureResumeAudio, { capture: true });
                this._gestureResumeAudio = null;
            }
        },
        methods: {
            slotForRow(ws, rowIdx) {
                const slots = ws.slots || [];
                if (slots.length !== 3) return null;
                return slots[rowIdx] || null;
            },
            cellOn(ws, rowIdx) {
                const s = this.slotForRow(ws, rowIdx);
                return s ? !!s.active : false;
            },
            roleDisplayName(role) {
                if (role == null || role === undefined) return '';
                const r = String(role).toUpperCase();
                if (r === 'ENGINEER') return 'Engineering';
                if (r === 'LEADER') return 'Leader';
                if (r === 'QUALITY') return 'Quality Controller';
                return String(role);
            },
            cellAriaLabel(ws, rowIdx) {
                if (!ws) return '';
                const s = this.slotForRow(ws, rowIdx);
                if (!s) return '';
                return (
                    this.roleDisplayName(s.role) +
                    ', input bit ' +
                    s.inputBitIndex +
                    ', output slave ' +
                    s.outputSlaveId +
                    ', relay ' +
                    s.outputChannel
                );
            },
            /**
             * Rows 1–2: open upward. Col position: first column left-anchored, last column right-anchored
             * so tooltips are not clipped horizontally at the scroll edges.
             */
            cellTooltipRootClass(rowIdx, colIdx) {
                const n = this.workstations.length;
                const lastCol = colIdx === n - 1;
                const firstCol = n > 1 && colIdx === 0;

                const base = [
                    'pointer-events-none',
                    'absolute',
                    'z-[60]',
                    'w-max',
                    'min-w-[7.5rem]',
                    'max-w-[10.5rem]',
                    'rounded-md',
                    'border',
                    'border-gray-700/80',
                    'bg-gray-950',
                    'px-2',
                    'py-1.5',
                    'text-left',
                    'shadow-md',
                    'shadow-black/30',
                    'opacity-0',
                    'scale-[0.98]',
                    'transition-all',
                    'duration-300',
                    'ease-out',
                    'group-hover:opacity-100',
                    'group-hover:scale-100'
                ];
                let hAlign;
                if (lastCol) {
                    hAlign = ['right-0', 'left-auto'];
                } else if (firstCol) {
                    hAlign = ['left-0', 'right-auto'];
                } else {
                    hAlign = ['left-1/2', '-translate-x-1/2'];
                }
                const merged = base.concat(hAlign);
                if (rowIdx >= 1) {
                    return merged.concat([
                        'bottom-full',
                        'mb-1.5',
                        'translate-y-1.5',
                        'group-hover:translate-y-0'
                    ]);
                }
                return merged.concat(['top-full', 'mt-1.5', '-translate-y-1.5', 'group-hover:translate-y-0']);
            },
            cellClasses(ws, rowIdx) {
                const on = ws ? this.cellOn(ws, rowIdx) : false;
                const base = ['matrix-cell__fill'];
                if (!on) {
                    return base.concat(['matrix-cell--off']);
                }
                const rowTone =
                    rowIdx === 0 ? 'matrix-cell--e' : rowIdx === 1 ? 'matrix-cell--l' : 'matrix-cell--q';
                return base.concat([rowTone]);
            },
            buildActiveAudioPlaylist(workstations) {
                const out = [];
                for (const ws of workstations) {
                    for (const slot of ws.slots || []) {
                        if (slot.active && slot.audioUrl) {
                            console.log('Active slot with audio:', slot.slotId, 'url:', slot.audioUrl, 'bit:', slot.inputBitIndex);
                            out.push({
                                key: String(slot.slotId),
                                url: slot.audioUrl,
                                bit: slot.inputBitIndex
                            });
                        }
                    }
                }
                out.sort((a, b) => a.bit - b.bit || String(a.key).localeCompare(String(b.key)));
                console.log('Built audio playlist:', out.length, 'items:', out);
                return out;
            },
            ensureAudioElement() {
                if (!this._audioEl) {
                    this._audioEl = new Audio();
                    this._audioEl.preload = 'auto';
                    this._audioEl.addEventListener('ended', () => this.onAlertAudioEnded());
                    this._audioEl.addEventListener('error', (e) => {
                        console.error('Audio playback error:', e, 'src:', this._audioEl?.src);
                        if (this._audioEl?.error) {
                            console.error('MediaError code:', this._audioEl.error.code, 'message:', this._audioEl.error.message);
                        }
                    });
                    this._audioEl.addEventListener('loadstart', () => {
                        console.log('Audio loading:', this._audioEl?.src);
                    });
                    this._audioEl.addEventListener('canplay', () => {
                        console.log('Audio ready to play:', this._audioEl?.src);
                    });
                }
                return this._audioEl;
            },
            resolvedAudioHref(url) {
                try {
                    return new URL(url, window.location.href).href;
                } catch (e) {
                    return url;
                }
            },
            /** Canonical URL for comparison — must match how the browser exposes audio.src after assignment. */
            normalizeAudioUrl(url) {
                if (url == null || url === '') {
                    return '';
                }
                try {
                    return new URL(url, window.location.href).href;
                } catch (e) {
                    return String(url);
                }
            },
            audioSrcMatches(audioEl, url) {
                if (!audioEl) {
                    return false;
                }
                try {
                    const want = this.normalizeAudioUrl(url);
                    if (!audioEl.src || audioEl.src === '') {
                        return want === '';
                    }
                    return new URL(audioEl.src).href === want;
                } catch (e) {
                    return false;
                }
            },
            /** Tracks HTMLMediaElement.play() promise — shows hint if browser blocks autoplay (NotAllowedError). */
            notifyPlayAttempt(playPromise) {
                if (!playPromise || typeof playPromise.then !== 'function') {
                    return;
                }
                playPromise
                    .then(() => {
                        this.showAudioGestureHint = false;
                    })
                    .catch((err) => {
                        const name = err && err.name;
                        const msg = (err && err.message) || '';
                        if (
                            name === 'NotAllowedError' ||
                            /not allowed|user gesture|interaction/i.test(msg)
                        ) {
                            this.showAudioGestureHint = true;
                        }
                    });
            },
            notifyPlayAttemptWithHandlers(playPromise, onSuccess, onFailure) {
                if (!playPromise || typeof playPromise.then !== 'function') {
                    return;
                }
                playPromise
                    .then(() => {
                        this.showAudioGestureHint = false;
                        if (typeof onSuccess === 'function') onSuccess();
                    })
                    .catch((err) => {
                        const name = err && err.name;
                        const msg = (err && err.message) || '';
                        if (
                            name === 'NotAllowedError' ||
                            /not allowed|user gesture|interaction/i.test(msg)
                        ) {
                            this.showAudioGestureHint = true;
                        }
                        if (typeof onFailure === 'function') onFailure(err);
                    });
            },
            clearAlertGapTimer() {
                if (this._alertGapTimer != null) {
                    clearTimeout(this._alertGapTimer);
                    this._alertGapTimer = null;
                }
            },
            clearRepeatTimer() {
                if (this._repeatTimer != null) {
                    clearTimeout(this._repeatTimer);
                    this._repeatTimer = null;
                }
            },
            playlistSignature(pl) {
                // Used to detect whether the "active audio set" has changed.
                // This signature is for scheduling decisions only.
                return (pl || [])
                    .map((x) => `${String(x.key)}|${this.normalizeAudioUrl(x.url)}`)
                    .join(';');
            },
            stopAlertAudio() {
                console.log('Stopping audio playback');
                this.showAudioGestureHint = false;
                this.clearAlertGapTimer();
                if (this._audioEl) {
                    this._audioEl.pause();
                    this._audioEl.currentTime = 0;
                    this._audioEl.removeAttribute('src');
                    this._audioEl.load();
                    this._audioEl = null;
                }
                this.playingKey = null;
                this.playlistIdx = 0;
                this._currentPlaylist = [];
                // Do not clear last-played timestamps; each audio has its own 15-minute timer.
                this._playQueue = [];
                this._queuedKeys = {};
                this._activeAudioByKey = {};
                // Clear any scheduled per-key replay timers.
                for (const key of Object.keys(this._replayTimerByKey || {})) {
                    clearTimeout(this._replayTimerByKey[key]);
                    delete this._replayTimerByKey[key];
                }
                this._replayCountByKey = {};
            },
            playFromPlaylistIndex(i) {
                const pl = this._currentPlaylist;
                if (!pl || !pl[i]) {
                    console.warn('playFromPlaylistIndex: invalid index or empty playlist', i, pl);
                    return;
                }
                this.clearAlertGapTimer();
                const a = this.ensureAudioElement();
                const item = pl[i];
                console.log('playFromPlaylistIndex:', i, 'of', pl.length, 'url:', item.url, 'key:', item.key);
                // We do "play once", so never loop tracks.
                a.loop = false;
                this.playlistIdx = i;
                this.playingKey = item.key;
                const sameSrc = this.audioSrcMatches(a, item.url);
                
                if (!sameSrc) {
                    console.log('Loading new audio source:', item.url);
                    a.pause();
                    a.currentTime = 0;
                    a.src = item.url;
                    a.load();
                    this.notifyPlayAttempt(a.play());
                } else if (a.paused || a.ended) {
                    console.log('Resuming audio playback');
                    this.notifyPlayAttempt(a.play());
                }
            },
            playAlertItem(item) {
                if (!item) return;
                if (this.playingKey != null) return; // safety: no overlap

                const a = this.ensureAudioElement();
                // Always play once; no looping.
                a.loop = false;
                this.playingKey = item.key;

                const sameSrc = this.audioSrcMatches(a, item.url);
                if (!sameSrc) {
                    console.log('Loading audio source:', item.url, 'key:', item.key);
                    a.pause();
                    a.currentTime = 0;
                    a.src = item.url;
                    a.load();
                } else if (!a.paused && !a.ended) {
                    // Already playing this src; avoid restarting.
                    return;
                }

                this.notifyPlayAttemptWithHandlers(
                    a.play(),
                    () => {
                        this._lastPlayedByKey[item.key] = Date.now();
                    },
                    () => {
                        if (this.playingKey === item.key) this.playingKey = null;
                    }
                );
            },
            tryPlayNextFromQueue() {
                // Never overlap: if currently playing, do nothing.
                if (this.playingKey != null) return;
                // If we're between tracks in the gap, wait.
                if (this._alertGapTimer != null) return;

                while (this._playQueue.length) {
                    const item = this._playQueue.shift();
                    if (!item) continue;
                    delete this._queuedKeys[item.key];

                    // Skip if no longer active right now.
                    if (!this.isAudioKeyActiveNow(item.key)) continue;

                    this.playAlertItem(item);
                    return;
                }
            },
            isAudioKeyActiveNow(audioKey) {
                const key = String(audioKey);
                for (const ws of this.workstations || []) {
                    for (const slot of ws.slots || []) {
                        if (!slot || !slot.active) continue;
                        if (String(slot.slotId) !== key) continue;
                        if (!slot.audioUrl) continue;
                        return true;
                    }
                }
                return false;
            },
            getActiveAudioItemByKey(audioKey) {
                const key = String(audioKey);
                for (const ws of this.workstations || []) {
                    for (const slot of ws.slots || []) {
                        if (!slot || !slot.active) continue;
                        if (String(slot.slotId) !== key) continue;
                        if (!slot.audioUrl) continue;
                        return {
                            key,
                            url: slot.audioUrl,
                            bit: slot.inputBitIndex
                        };
                    }
                }
                return null;
            },
            onAlertAudioEnded() {
                const endedKey = this.playingKey;
                console.log('Audio ended, playingKey:', endedKey);
                this.playingKey = null;
                this.playlistIdx = 0;

                // Start the per-audio 15-minute replay timer AFTER playback ends.
                const repeatMs = typeof this.repeatEveryMs === 'number' ? this.repeatEveryMs : 15 * 60 * 1000;
                const maxRepeats = typeof this.maxAlertRepeats === 'number' ? this.maxAlertRepeats : 4;
                if (endedKey != null) {
                    if (this._replayTimerByKey[endedKey] != null) {
                        clearTimeout(this._replayTimerByKey[endedKey]);
                        delete this._replayTimerByKey[endedKey];
                    }

                    this._replayTimerByKey[endedKey] = setTimeout(() => {
                        this._replayTimerByKey[endedKey] = null;

                        // If tab is hidden, don't start new audio.
                        if (document.hidden) return;

                        // Replay only if the switch is still ON and we have an active audio URL.
                        const item = this.getActiveAudioItemByKey(endedKey);
                        if (!item) return;

                        // Stop repeating this key after max repeats.
                        const replayCount = Number(this._replayCountByKey[endedKey] || 0);
                        if (replayCount >= maxRepeats) return;

                        // Avoid overlap / duplicates.
                        if (this.playingKey === item.key) return;
                        if (this._queuedKeys[item.key]) return;

                        this._playQueue.push(item);
                        this._queuedKeys[item.key] = true;
                        this._replayCountByKey[endedKey] = replayCount + 1;

                        // Deterministic order (optional but stable).
                        this._playQueue.sort((a, b) => a.bit - b.bit || String(a.key).localeCompare(String(b.key)));

                        this.tryPlayNextFromQueue();
                    }, repeatMs);
                }

                const gap = typeof this.alertGapMs === 'number' ? this.alertGapMs : 400;
                this.clearAlertGapTimer();
                this._alertGapTimer = setTimeout(() => {
                    this._alertGapTimer = null;
                    this.tryPlayNextFromQueue();
                }, gap);
            },
            syncAlertAudioPlaylist(workstations) {
                const pl = this.buildActiveAudioPlaylist(workstations);
                if (pl.length === 0) {
                    console.log('No active audio, stopping playback');
                    this.stopAlertAudio();
                    return;
                }

                // Detect per-key transitions so each OFF->ON reset plays immediately.
                const prevActiveByKey = this._activeAudioByKey || {};
                const activeByKey = {};
                const activeNowSet = new Set();
                for (const item of pl) {
                    activeByKey[item.key] = item;
                    activeNowSet.add(item.key);
                }
                this._activeAudioByKey = activeByKey;

                // Purge queued items that are no longer active.
                if (this._playQueue.length) {
                    this._playQueue = this._playQueue.filter((it) => activeNowSet.has(it.key));
                }
                for (const key of Object.keys(this._queuedKeys)) {
                    if (!activeNowSet.has(key)) delete this._queuedKeys[key];
                }

                // OFF transitions: cancel any scheduled replay timer for that key.
                for (const key of Object.keys(prevActiveByKey)) {
                    if (activeNowSet.has(key)) continue;
                    if (this._replayTimerByKey[key] != null) {
                        clearTimeout(this._replayTimerByKey[key]);
                        delete this._replayTimerByKey[key];
                    }
                    delete this._replayCountByKey[key];
                    delete this._queuedKeys[key];
                }

                // ON transitions: enqueue immediate playback for newly active keys.
                for (const item of pl) {
                    const wasActive = !!prevActiveByKey[item.key];
                    if (wasActive) continue;

                    // If a timer exists (e.g., due to timing races), clear it; new timer starts after this playback ends.
                    if (this._replayTimerByKey[item.key] != null) {
                        clearTimeout(this._replayTimerByKey[item.key]);
                        delete this._replayTimerByKey[item.key];
                    }
                    this._replayCountByKey[item.key] = 0;

                    if (item.key === this.playingKey) continue;
                    if (this._queuedKeys[item.key]) continue;

                    this._playQueue.push(item);
                    this._queuedKeys[item.key] = true;
                }

                // Deterministic ordering: by bit then key.
                if (this._playQueue.length) {
                    this._playQueue.sort((a, b) => a.bit - b.bit || String(a.key).localeCompare(String(b.key)));
                }

                // Start next item if nothing is playing.
                this.tryPlayNextFromQueue();
            },
            async refresh() {
                if (this._refreshInFlight) {
                    return;
                }
                this._refreshInFlight = true;
                try {
                    const res = await fetch('/api/dashboard', { cache: 'no-store' });
                    if (!res.ok) {
                        console.warn('Dashboard refresh failed:', res.status);
                        return;
                    }
                    const j = await res.json();
                    const next = j.workstations || [];
                    console.log('Dashboard refresh:', next.length, 'workstations');
                    this.workstations = next;

                    if (typeof j.alertRepeatIntervalMinutes === 'number' && j.alertRepeatIntervalMinutes > 0) {
                        this.repeatEveryMs = j.alertRepeatIntervalMinutes * 60 * 1000;
                    }
                    if (typeof j.alertMaxRepeats === 'number' && j.alertMaxRepeats >= 0) {
                        this.maxAlertRepeats = j.alertMaxRepeats;
                    }

                    if (document.hidden) {
                        console.log('Page hidden, stopping audio');
                        this.stopAlertAudio();
                    } else {
                        this.syncAlertAudioPlaylist(next);
                    }
                    
                    this.modbusConnected = !!j.modbusConnected;
                    this.modbusError = j.modbusError || null;
                } catch (e) {
                    console.error('Dashboard refresh error:', e);
                } finally {
                    this._refreshInFlight = false;
                }
            }
        }
    }).mount('#app');
})();
