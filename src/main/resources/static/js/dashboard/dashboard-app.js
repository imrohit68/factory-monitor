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

    function parseAlertGapMs(el) {
        const v = el ? el.getAttribute('data-alert-gap-ms') : null;
        if (v == null || v === '') return 400;
        const n = parseInt(v, 10);
        return Number.isFinite(n) && n >= 0 ? n : 400;
    }

    const repeatIntervalMin = parseAlertRepeatIntervalMinutes(appEl);
    const boot = {
        modbusConnected: !!(appEl && appEl.getAttribute('data-modbus-connected') === 'true'),
        modbusError: errAttr || null,
        repeatEveryMs: repeatIntervalMin * 60 * 1000,
        maxAlertRepeats: parseAlertMaxRepeats(appEl),
        alertGapMs: parseAlertGapMs(appEl)
    };
    const SPEAKER_FRAME_COUNT = 26;
    const SPEAKER_FRAME_MS = 70;

    const { createApp } = Vue;

    createApp({
        data() {
            return {
                workstations: [],
                playingKey: null,
                alertGapMs: boot.alertGapMs,
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
                _audioEl: null,
                _playWatchdogTimer: null,
                _currentPlayGuard: null,
                _lastAppliedPlaySequence: null,
                /** While awaiting successful clip-ended POST for this sequence (avoid replaying same PLAY). */
                _pendingEndedAckSequence: null,
                _currentServerClip: null,
                _mainPollInterval: null,
                _hintPollInterval: null,
                _visibilityHandler: null,
                _beforeUnloadHandler: null,
                _gestureResumeAudio: null,
                showAudioGestureHint: false,
                _refreshInFlight: false,
                mode: 'PRODUCTION',
                speakerFrameIndex: 0,
                _speakerAnimTimer: null
            };
        },
        computed: {
            speakerPlayingSrc() {
                const idx = String(this.speakerFrameIndex).padStart(2, '0');
                return '/images/speaker-frame-' + idx + '.png';
            }
        },
        watch: {
            playingKey(nextKey) {
                if (nextKey != null) {
                    this.startSpeakerAnimation();
                    return;
                }
                this.stopSpeakerAnimation();
            },
            showAudioGestureHint(on) {
                if (on) {
                    if (this._hintPollInterval != null) return;
                    this._hintPollInterval = setInterval(() => this.refresh(), 750);
                    return;
                }
                if (this._hintPollInterval != null) {
                    clearInterval(this._hintPollInterval);
                    this._hintPollInterval = null;
                }
            }
        },
        mounted() {
            this.refresh();
            this._mainPollInterval = setInterval(() => this.refresh(), 2000);

            this._visibilityHandler = () => {
                if (document.hidden) {
                    console.log('Page hidden event (alert audio continues in background)');
                    return;
                }
                this.refresh();
            };
            document.addEventListener('visibilitychange', this._visibilityHandler);

            this._beforeUnloadHandler = () => {
                this.stopAlertAudio();
            };
            window.addEventListener('beforeunload', this._beforeUnloadHandler);
            window.addEventListener('pagehide', this._beforeUnloadHandler);

            this._gestureResumeAudio = (e) => {
                if (document.hidden) return;
                if (e.type === 'keydown' && e.key !== 'Enter' && e.key !== ' ') return;
                this.refresh();
            };
            document.addEventListener('pointerdown', this._gestureResumeAudio, { capture: true, passive: true });
            document.addEventListener('keydown', this._gestureResumeAudio, { capture: true });
        },
        beforeUnmount() {
            this.stopSpeakerAnimation();
            this.stopAlertAudio();
            if (this._mainPollInterval != null) {
                clearInterval(this._mainPollInterval);
                this._mainPollInterval = null;
            }
            if (this._hintPollInterval != null) {
                clearInterval(this._hintPollInterval);
                this._hintPollInterval = null;
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
            startSpeakerAnimation() {
                if (this._speakerAnimTimer != null) {
                    return;
                }
                this.speakerFrameIndex = 0;
                this._speakerAnimTimer = setInterval(() => {
                    this.speakerFrameIndex = (this.speakerFrameIndex + 1) % SPEAKER_FRAME_COUNT;
                }, SPEAKER_FRAME_MS);
            },
            stopSpeakerAnimation() {
                if (this._speakerAnimTimer != null) {
                    clearInterval(this._speakerAnimTimer);
                    this._speakerAnimTimer = null;
                }
                this.speakerFrameIndex = 0;
            },
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
            cellTooltipRootClass(rowIdx, colIdx) {
                const wsArr = Array.isArray(this.workstations) ? this.workstations : [];
                const n = wsArr.length;
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
            async toggleSimulation(slot) {
                if (this.mode !== 'MAINTENANCE' || !slot || !slot.slotId) return;
                try {
                    const res = await fetch('/api/dashboard/simulation/toggle/' + slot.slotId, { method: 'POST' });
                    if (!res.ok) {
                        console.warn('Simulation toggle failed:', res.status);
                        return;
                    }
                    await this.refresh();
                } catch (e) {
                    console.error('Simulation toggle error:', e);
                }
            },
            ensureAudioElement() {
                if (!this._audioEl) {
                    this._audioEl = new Audio();
                    this._audioEl.preload = 'auto';
                    this._audioEl.addEventListener('ended', () => this.onAlertAudioEnded());
                    this._audioEl.addEventListener('error', (e) => {
                        console.error('Audio playback error:', e, 'src:', this._audioEl?.src);
                        if (this._audioEl?.error) {
                            console.error(
                                'MediaError code:',
                                this._audioEl.error.code,
                                'message:',
                                this._audioEl.error.message
                            );
                        }
                    });
                }
                return this._audioEl;
            },
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
            async notifyServerClipEnded(clip) {
                if (!clip || clip.key == null || clip.activationId == null) return;
                const body = JSON.stringify({
                    key: String(clip.key),
                    activationId: String(clip.activationId)
                });
                for (let attempt = 0; attempt < 3; attempt++) {
                    try {
                        const res = await fetch('/api/dashboard/playback-sync/ended', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: body
                        });
                        if (res.ok) {
                            return;
                        }
                    } catch (e) {
                        console.warn('clip-ended POST attempt failed:', e);
                    }
                    await new Promise((r) => setTimeout(r, 400));
                }
                console.warn('clip-ended POST failed after retries');
            },
            /** Drop local playback without notifying the server (new PLAY sequence or page unload). */
            abortLocalPlaybackWithoutNotify() {
                if (this._currentPlayGuard) this._currentPlayGuard.cancelled = true;
                if (this._playWatchdogTimer != null) {
                    clearTimeout(this._playWatchdogTimer);
                    this._playWatchdogTimer = null;
                }
                if (this._audioEl) {
                    try {
                        this._audioEl.pause();
                    } catch (e) {
                        /* ignore */
                    }
                    this._audioEl.removeAttribute('src');
                    try {
                        this._audioEl.load();
                    } catch (e) {
                        /* ignore */
                    }
                    this._audioEl = null;
                }
                this.playingKey = null;
                this._currentServerClip = null;
                this._lastAppliedPlaySequence = null;
                this._pendingEndedAckSequence = null;
            },
            stopAlertAudio() {
                this.showAudioGestureHint = false;
                this.abortLocalPlaybackWithoutNotify();
            },
            applyServerAlertAudio(cmd) {
                const c = cmd && typeof cmd === 'object' ? cmd : {};
                const action = c.action === 'PLAY' ? 'PLAY' : 'IDLE';
                if (action === 'IDLE') {
                    if (c.waitingAfterClipGap) {
                        return;
                    }
                    if (this.playingKey != null) {
                        this.abortLocalPlaybackWithoutNotify();
                    }
                    return;
                }
                const seq = Number(c.sequence);
                if (!Number.isFinite(seq)) {
                    return;
                }
                const slotKey = String(c.slotKey);
                if (
                    this._lastAppliedPlaySequence === seq &&
                    (this.playingKey === slotKey || this._pendingEndedAckSequence === seq)
                ) {
                    return;
                }
                if (this.playingKey != null && this._lastAppliedPlaySequence !== seq) {
                    this.abortLocalPlaybackWithoutNotify();
                }
                this.playServerAlertCommand(c, seq, slotKey);
            },
            playServerAlertCommand(cmd, sequence, slotKey) {
                if (this.playingKey != null) {
                    return;
                }
                const url = cmd.audioUrl;
                const activationId = cmd.activationId != null ? String(cmd.activationId) : null;
                if (!url || !activationId) {
                    return;
                }

                const a = this.ensureAudioElement();
                a.loop = false;
                this.playingKey = slotKey;

                try {
                    a.pause();
                    a.currentTime = 0;
                    if (this.audioSrcMatches(a, url)) {
                        a.removeAttribute('src');
                        a.load();
                    }
                    a.muted = false;
                    if (typeof a.volume === 'number') {
                        a.volume = 1;
                    }
                    a.src = url;
                    a.load();
                } catch (e) {
                    /* ignore */
                }

                const guard = { cancelled: false };
                this._currentPlayGuard = guard;

                const armWatchdog = (durationSeconds) => {
                    if (guard.cancelled) return;
                    const safeMs =
                        Number.isFinite(durationSeconds) && durationSeconds > 0
                            ? durationSeconds * 1000 + 500
                            : 8000;
                    this._playWatchdogTimer = setTimeout(() => {
                        if (guard.cancelled) return;
                        console.warn('JavaFX watchdog fired — ended event was not received');
                        this.onAlertAudioEnded();
                    }, safeMs);
                };

                const clearWatchdog = () => {
                    if (this._playWatchdogTimer != null) {
                        clearTimeout(this._playWatchdogTimer);
                        this._playWatchdogTimer = null;
                    }
                };

                const onEndedOnce = () => {
                    a.removeEventListener('ended', onEndedOnce);
                    if (guard.cancelled) return;
                    guard.cancelled = true;
                    clearWatchdog();
                };
                a.addEventListener('ended', onEndedOnce);

                const beginPlayback = () => {
                    if (guard.cancelled) return;
                    if (a.duration && Number.isFinite(a.duration)) {
                        armWatchdog(a.duration);
                    } else {
                        const onMeta = () => {
                            a.removeEventListener('loadedmetadata', onMeta);
                            armWatchdog(a.duration);
                        };
                        a.addEventListener('loadedmetadata', onMeta);
                    }

                    this.notifyPlayAttemptWithHandlers(
                        a.play(),
                        () => {
                            this._lastAppliedPlaySequence = sequence;
                            this._currentServerClip = { key: slotKey, activationId: activationId };
                        },
                        () => {
                            guard.cancelled = true;
                            clearWatchdog();
                            if (this.playingKey === slotKey) this.playingKey = null;
                        }
                    );
                };

                beginPlayback();
            },
            async onAlertAudioEnded() {
                const clip = this._currentServerClip;
                const endedKey = this.playingKey;
                const ackSeq = this._lastAppliedPlaySequence;
                if (ackSeq != null) {
                    this._pendingEndedAckSequence = ackSeq;
                }
                const a = this._audioEl;
                if (a) {
                    try {
                        a.pause();
                    } catch (e) {
                        /* ignore */
                    }
                }
                console.log('Audio ended, playingKey:', endedKey);
                this.playingKey = null;
                try {
                    await this.notifyServerClipEnded(clip);
                } finally {
                    this._pendingEndedAckSequence = null;
                }
                this._currentServerClip = null;
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
                    const next = Array.isArray(j.workstations) ? j.workstations : [];
                    this.workstations = next;
                    if (j.mode) {
                        this.mode = j.mode;
                    }

                    if (typeof j.alertRepeatIntervalMinutes === 'number' && j.alertRepeatIntervalMinutes > 0) {
                        this.repeatEveryMs = j.alertRepeatIntervalMinutes * 60 * 1000;
                    }
                    if (typeof j.alertMaxRepeats === 'number' && j.alertMaxRepeats >= 0) {
                        this.maxAlertRepeats = j.alertMaxRepeats;
                    }
                    if (typeof j.alertGapMs === 'number' && j.alertGapMs >= 0) {
                        this.alertGapMs = j.alertGapMs;
                    }

                    this.applyServerAlertAudio(j.alertAudio);

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
