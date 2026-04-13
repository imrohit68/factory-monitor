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
    const SPEAKER_FRAME_COUNT = 26;
    const SPEAKER_FRAME_MS = 70;
    /** Shared activation snapshot so refresh, route return, and new tabs preserve the same ON episode. */
    const DASHBOARD_ALERT_ACTIVATION_KEY = 'dashboard-alert-activation-v1';
    const DASHBOARD_ALERT_PLAYBACK_KEY = 'dashboard-alert-playback-v1';
    const DASHBOARD_ALERT_PLAYBACK_CHANNEL = 'dashboard-alert-playback-v1';
    const DASHBOARD_ALERT_PLAYBACK_PUBLISH_MS = 400;
    const DASHBOARD_ALERT_PLAYBACK_SERVER_PUBLISH_MS = 800;
    /** Must exceed dashboard poll interval (2s) and network slack; keep in line with server-side playback TTL. */
    const DASHBOARD_ALERT_PLAYBACK_STALE_MS = 15000;

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
                _playWatchdogTimer: null,
                _currentPlayGuard: null,
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
                /** Mirrors activation snapshot when localStorage is unavailable (e.g. JavaFX WebView). */
                _activationSnapshotMemory: {},
                _playbackSyncChannel: null,
                _playbackSyncSenderId:
                    'dashboard-tab-' + Date.now() + '-' + Math.random().toString(36).slice(2),
                _playbackSyncPublishTimer: null,
                _lastServerPlaybackPublishAt: 0,
                _serverPlaybackState: null,
                /** serverTimeMs from last /api/dashboard minus local Date.now() — aligns playback sync across hosts/clocks. */
                _serverTimeSkewMs: 0,
                _storagePlaybackHandler: null,
                _broadcastPlaybackHandler: null,
                _pollInterval: null,
                _visibilityHandler: null,
                _beforeUnloadHandler: null,
                _gestureResumeAudio: null,
                /** Shown when alert audio could not start (browser autoplay policy). Cleared after first successful play(). */
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
            }
        },
        mounted() {
            this.refresh();
            this._pollInterval = setInterval(() => this.refresh(), 2000);
            this.setupPlaybackSync();
            
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
            this.stopSpeakerAnimation();
            this.stopAlertAudio();
            this.teardownPlaybackSync();
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
            buildActiveAudioPlaylist(workstations) {
                const out = [];
                for (const ws of workstations) {
                    for (const slot of ws.slots || []) {
                        if (slot.active && slot.audioUrl) {
                            console.log('Active slot with audio:', slot.slotId, 'url:', slot.audioUrl, 'bit:', slot.inputBitIndex);
                            out.push({
                                key: String(slot.slotId),
                                url: slot.audioUrl,
                                bit: slot.inputBitIndex,
                                activationId:
                                    slot.openEventId == null || slot.openEventId === undefined
                                        ? null
                                        : String(slot.openEventId)
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
            /** Path + query only so 127.0.0.1 vs localhost (and other same-resource URLs) still match for sync. */
            normalizeAudioUrlPathKey(url) {
                if (url == null || url === '') {
                    return '';
                }
                try {
                    const u = new URL(url, window.location.href);
                    let path = u.pathname + u.search;
                    if (path !== '' && !path.startsWith('/')) {
                        path = '/' + path;
                    }
                    return path;
                } catch (e) {
                    const s = String(url);
                    return s.startsWith('/') ? s : '/' + s;
                }
            },
            alignedNowMs() {
                return Date.now() + (typeof this._serverTimeSkewMs === 'number' ? this._serverTimeSkewMs : 0);
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
            setupPlaybackSync() {
                if (typeof window !== 'undefined') {
                    this._storagePlaybackHandler = (e) => {
                        if (e.key !== DASHBOARD_ALERT_PLAYBACK_KEY || !e.newValue) return;
                        this.onSharedPlaybackSignal(e.newValue);
                    };
                    window.addEventListener('storage', this._storagePlaybackHandler);
                }
                if (typeof BroadcastChannel !== 'undefined') {
                    try {
                        this._playbackSyncChannel = new BroadcastChannel(DASHBOARD_ALERT_PLAYBACK_CHANNEL);
                        this._broadcastPlaybackHandler = (e) => this.onSharedPlaybackSignal(e && e.data);
                        this._playbackSyncChannel.addEventListener('message', this._broadcastPlaybackHandler);
                    } catch (e) {
                        console.warn('Failed to open dashboard playback sync channel:', e);
                        this._playbackSyncChannel = null;
                        this._broadcastPlaybackHandler = null;
                    }
                }
            },
            teardownPlaybackSync() {
                this.clearPlaybackSyncPublisher();
                if (this._playbackSyncChannel && this._broadcastPlaybackHandler) {
                    this._playbackSyncChannel.removeEventListener('message', this._broadcastPlaybackHandler);
                }
                if (this._playbackSyncChannel) {
                    try {
                        this._playbackSyncChannel.close();
                    } catch (e) {
                        /* ignore */
                    }
                    this._playbackSyncChannel = null;
                }
                this._broadcastPlaybackHandler = null;
                if (typeof window !== 'undefined' && this._storagePlaybackHandler) {
                    window.removeEventListener('storage', this._storagePlaybackHandler);
                }
                this._storagePlaybackHandler = null;
            },
            normalizePlaybackState(raw) {
                if (!raw) return null;
                let parsed = raw;
                if (typeof raw === 'string') {
                    try {
                        parsed = JSON.parse(raw);
                    } catch (e) {
                        return null;
                    }
                }
                if (!parsed || typeof parsed !== 'object') return null;
                const key = parsed.key == null || parsed.key === '' ? null : String(parsed.key);
                const url = this.normalizeAudioUrl(parsed.url);
                const activationId =
                    parsed.activationId == null || parsed.activationId === ''
                        ? null
                        : String(parsed.activationId);
                const wallClockStartMs = Number(parsed.wallClockStartMs);
                const updatedAtMs = Number(parsed.updatedAtMs);
                const senderId =
                    parsed.senderId == null || parsed.senderId === ''
                        ? null
                        : String(parsed.senderId);
                if (!key || !url) return null;
                if (!Number.isFinite(wallClockStartMs) || !Number.isFinite(updatedAtMs)) return null;
                return {
                    key,
                    url,
                    activationId,
                    wallClockStartMs,
                    updatedAtMs,
                    senderId
                };
            },
            readSharedPlaybackState() {
                if (typeof window === 'undefined' || !window.localStorage) {
                    return null;
                }
                try {
                    return this.normalizePlaybackState(window.localStorage.getItem(DASHBOARD_ALERT_PLAYBACK_KEY));
                } catch (e) {
                    console.warn('Failed to read shared dashboard playback state:', e);
                    return null;
                }
            },
            bestSharedPlaybackState() {
                let best = null;
                for (const candidate of arguments) {
                    const normalized = this.normalizePlaybackState(candidate);
                    if (!normalized || !this.isPlaybackStateFresh(normalized)) {
                        continue;
                    }
                    if (!best || normalized.updatedAtMs > best.updatedAtMs) {
                        best = normalized;
                    }
                }
                return best;
            },
            writeSharedPlaybackState(state) {
                if (!state) {
                    return;
                }
                if (typeof window !== 'undefined' && window.localStorage) {
                    const payload = JSON.stringify(state);
                    try {
                        window.localStorage.setItem(DASHBOARD_ALERT_PLAYBACK_KEY, payload);
                    } catch (e) {
                        console.warn('Failed to write shared dashboard playback state:', e);
                    }
                }
                if (this._playbackSyncChannel) {
                    try {
                        this._playbackSyncChannel.postMessage(state);
                    } catch (e) {
                        console.warn('Failed to broadcast dashboard playback state:', e);
                    }
                }
            },
            async publishPlaybackStateToServer(state) {
                if (!state) {
                    return;
                }
                try {
                    await fetch('/api/dashboard/playback-sync', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(state)
                    });
                } catch (e) {
                    console.warn('Failed to publish dashboard playback state to server:', e);
                }
            },
            async clearPlaybackStateOnServer() {
                if (!this._playbackSyncSenderId) {
                    return;
                }
                try {
                    await fetch('/api/dashboard/playback-sync/clear', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ senderId: this._playbackSyncSenderId })
                    });
                } catch (e) {
                    console.warn('Failed to clear dashboard playback state on server:', e);
                }
            },
            isPlaybackStateFresh(state) {
                if (!state) return false;
                const ageMs = this.alignedNowMs() - state.updatedAtMs;
                return ageMs >= -1000 && ageMs <= DASHBOARD_ALERT_PLAYBACK_STALE_MS;
            },
            samePlaybackActivation(state, item) {
                if (!state || !item) return false;
                if (String(state.key) !== String(item.key)) return false;
                if (this.normalizeAudioUrlPathKey(state.url) !== this.normalizeAudioUrlPathKey(item.url)) {
                    return false;
                }
                const stateActivation = state.activationId == null ? null : String(state.activationId);
                const itemActivation = item.activationId == null ? null : String(item.activationId);
                return stateActivation === itemActivation;
            },
            computeJoinSeekSeconds(state) {
                if (!state) return null;
                const target = (this.alignedNowMs() - state.wallClockStartMs) / 1000;
                if (!Number.isFinite(target) || target < 0) {
                    return null;
                }
                return target;
            },
            publishPlaybackState(item, currentTimeSeconds) {
                if (!item) return;
                const now = Date.now();
                const currentSec =
                    Number.isFinite(currentTimeSeconds) && currentTimeSeconds >= 0
                        ? currentTimeSeconds
                        : 0;
                const pathKey = this.normalizeAudioUrlPathKey(item.url);
                const state = {
                    key: String(item.key),
                    url: pathKey || this.normalizeAudioUrl(item.url),
                    activationId: item.activationId == null ? null : String(item.activationId),
                    wallClockStartMs: now - Math.round(currentSec * 1000),
                    updatedAtMs: now,
                    senderId: this._playbackSyncSenderId
                };
                if (!document.hidden) {
                    this.writeSharedPlaybackState(state);
                }
                this._serverPlaybackState = state;
                if (
                    this._lastServerPlaybackPublishAt === 0 ||
                    (now - this._lastServerPlaybackPublishAt) >= DASHBOARD_ALERT_PLAYBACK_SERVER_PUBLISH_MS
                ) {
                    this._lastServerPlaybackPublishAt = now;
                    void this.publishPlaybackStateToServer(state);
                }
            },
            clearPlaybackSyncPublisher() {
                if (this._playbackSyncPublishTimer != null) {
                    clearInterval(this._playbackSyncPublishTimer);
                    this._playbackSyncPublishTimer = null;
                }
            },
            startPlaybackSyncPublisher(item, initialCurrentTimeSeconds) {
                this.clearPlaybackSyncPublisher();
                let firstTick = true;
                const publish = () => {
                    if (this.playingKey !== item.key) return;
                    const a = this._audioEl;
                    if (!a) return;
                    let currentTimeSeconds =
                        Number.isFinite(a.currentTime) && a.currentTime >= 0 ? a.currentTime : 0;
                    if (
                        firstTick &&
                        Number.isFinite(initialCurrentTimeSeconds) &&
                        initialCurrentTimeSeconds > currentTimeSeconds
                    ) {
                        currentTimeSeconds = initialCurrentTimeSeconds;
                    }
                    this.publishPlaybackState(item, currentTimeSeconds);
                    firstTick = false;
                };
                publish();
                this._playbackSyncPublishTimer = setInterval(
                    publish,
                    DASHBOARD_ALERT_PLAYBACK_PUBLISH_MS
                );
            },
            maybeJoinFromPlaybackState(state) {
                if (document.hidden) return false;
                const normalized = this.normalizePlaybackState(state);
                if (!normalized || !this.isPlaybackStateFresh(normalized)) {
                    return false;
                }
                if (normalized.senderId === this._playbackSyncSenderId) {
                    return false;
                }
                const item = this.getActiveAudioItemByKey(normalized.key);
                if (!item) return false;
                if (!this.samePlaybackActivation(normalized, item)) return false;
                if (this.playingKey != null) return false;
                if (this._queuedKeys[item.key]) return false;
                this.playAlertItem(item, { syncState: normalized });
                return true;
            },
            onSharedPlaybackSignal(rawState) {
                this.maybeJoinFromPlaybackState(rawState);
            },
            readPersistedActivationSnapshot() {
                const mem = this._activationSnapshotMemory && typeof this._activationSnapshotMemory === 'object'
                    ? this._activationSnapshotMemory
                    : {};
                let fromDisk = {};
                if (typeof window !== 'undefined' && window.localStorage) {
                    try {
                        const raw = window.localStorage.getItem(DASHBOARD_ALERT_ACTIVATION_KEY);
                        if (raw) {
                            const parsed = JSON.parse(raw);
                            const byKey = parsed && typeof parsed === 'object' ? parsed.byKey : null;
                            if (byKey && typeof byKey === 'object') {
                                for (const [k, v] of Object.entries(byKey)) {
                                    if (!v || typeof v !== 'object') continue;
                                    fromDisk[String(k)] = {
                                        url: this.normalizeAudioUrl(v.url),
                                        activationId:
                                            v.activationId == null || v.activationId === ''
                                                ? null
                                                : String(v.activationId)
                                    };
                                }
                            }
                        }
                    } catch (e) {
                        console.warn('Failed to read dashboard alert activation snapshot:', e);
                    }
                }
                if (Object.keys(fromDisk).length > 0) {
                    return Object.assign({}, mem, fromDisk);
                }
                return Object.assign({}, mem);
            },
            writePersistedActivationSnapshot(byKey) {
                const normalized = {};
                for (const [rawKey, v] of Object.entries(byKey || {})) {
                    if (!v || typeof v !== 'object') continue;
                    normalized[String(rawKey)] = {
                        url: this.normalizeAudioUrl(v.url),
                        activationId: v.activationId == null ? null : String(v.activationId)
                    };
                }
                this._activationSnapshotMemory = normalized;
                if (typeof window === 'undefined' || !window.localStorage) {
                    return;
                }
                try {
                    window.localStorage.setItem(
                        DASHBOARD_ALERT_ACTIVATION_KEY,
                        JSON.stringify({ byKey: normalized })
                    );
                } catch (e) {
                    console.warn('Failed to write dashboard alert activation snapshot:', e);
                }
            },
            sameActivationPersisted(persistedEntry, item) {
                if (!persistedEntry || !item) {
                    return false;
                }
                if (this.normalizeAudioUrlPathKey(persistedEntry.url) !== this.normalizeAudioUrlPathKey(item.url)) {
                    return false;
                }
                const a = item.activationId == null ? null : String(item.activationId);
                const b = persistedEntry.activationId;
                if (a != null && b != null) {
                    return a === b;
                }
                if (a == null && b == null) {
                    return true;
                }
                return false;
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
                this.clearPlaybackSyncPublisher();
                this._lastServerPlaybackPublishAt = 0;
                this._serverPlaybackState = null;
                void this.clearPlaybackStateOnServer();
                if (this._currentPlayGuard) this._currentPlayGuard.cancelled = true;
                if (this._playWatchdogTimer != null) {
                    clearTimeout(this._playWatchdogTimer);
                    this._playWatchdogTimer = null;
                }
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
            playAlertItem(item, options) {
                if (!item) return;
                if (this.playingKey != null) return;
                const syncState =
                    options && options.syncState ? this.normalizePlaybackState(options.syncState) : null;

                const a = this.ensureAudioElement();
                a.loop = false;
                this.playingKey = item.key;

                // JavaFX WebView: second play of the same URL often stays silent if we only seek to 0.
                // Clear and reload the resource so decode/output restarts reliably.
                try {
                    a.pause();
                    a.currentTime = 0;
                    if (this.audioSrcMatches(a, item.url)) {
                        a.removeAttribute('src');
                        a.load();
                    }
                    a.muted = false;
                    if (typeof a.volume === 'number') {
                        a.volume = 1;
                    }
                    a.src = item.url;
                    a.load();
                } catch (e) {
                    /* ignore */
                }

                const guard = { cancelled: false };
                this._currentPlayGuard = guard;

                const armWatchdog = (durationSeconds) => {
                    if (guard.cancelled) return;
                    const safeMs = Number.isFinite(durationSeconds) && durationSeconds > 0
                        ? (durationSeconds * 1000) + 500
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

                const beginPlayback = (initialCurrentTimeSeconds) => {
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
                            this._lastPlayedByKey[item.key] = Date.now();
                            this.startPlaybackSyncPublisher(item, initialCurrentTimeSeconds);
                        },
                        () => {
                            guard.cancelled = true;
                            clearWatchdog();
                            this.clearPlaybackSyncPublisher();
                            if (this.playingKey === item.key) this.playingKey = null;
                        }
                    );
                };

                if (syncState && this.samePlaybackActivation(syncState, item)) {
                    const startJoinedPlayback = () => {
                        if (guard.cancelled) return;
                        let joinSeconds = this.computeJoinSeekSeconds(syncState);
                        if (!Number.isFinite(joinSeconds) || joinSeconds < 0) {
                            joinSeconds = 0;
                        }
                        if (Number.isFinite(a.duration) && a.duration > 0) {
                            if (joinSeconds >= a.duration) {
                                guard.cancelled = true;
                                a.removeEventListener('ended', onEndedOnce);
                                clearWatchdog();
                                if (this.playingKey === item.key) this.playingKey = null;
                                return;
                            }
                            joinSeconds = Math.min(joinSeconds, Math.max(0, a.duration - 0.05));
                        }
                        try {
                            a.currentTime = joinSeconds;
                        } catch (e) {
                            /* ignore */
                        }
                        beginPlayback(joinSeconds);
                    };
                    if (a.readyState >= 1) {
                        startJoinedPlayback();
                    } else {
                        const onMeta = () => {
                            a.removeEventListener('loadedmetadata', onMeta);
                            startJoinedPlayback();
                        };
                        a.addEventListener('loadedmetadata', onMeta);
                    }
                    return;
                }

                beginPlayback(0);
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
                            bit: slot.inputBitIndex,
                            activationId:
                                slot.openEventId == null || slot.openEventId === undefined
                                    ? null
                                    : String(slot.openEventId)
                        };
                    }
                }
                return null;
            },
            onAlertAudioEnded() {
                // If we left the element "playing" (e.g. watchdog fired before a real `ended` in WebView),
                // pause so the next replay does not hit playAlertItem's same-src early-return with playingKey stuck.
                const a = this._audioEl;
                if (a) {
                    try {
                        a.pause();
                    } catch (e) {
                        /* ignore */
                    }
                }
                this.clearPlaybackSyncPublisher();
                this._lastServerPlaybackPublishAt = 0;
                this._serverPlaybackState = null;
                void this.clearPlaybackStateOnServer();
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
                    this.writePersistedActivationSnapshot({});
                    this.stopAlertAudio();
                    return;
                }

                // Shared snapshot so refresh, route return, and new tabs keep the same ON episode.
                const prevPersisted = this.readPersistedActivationSnapshot();
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
                for (const key of Object.keys(prevPersisted)) {
                    if (activeNowSet.has(key)) continue;
                    if (this._replayTimerByKey[key] != null) {
                        clearTimeout(this._replayTimerByKey[key]);
                        delete this._replayTimerByKey[key];
                    }
                    delete this._replayCountByKey[key];
                    delete this._queuedKeys[key];
                }

                const sharedPlayback = this.bestSharedPlaybackState(
                    this.readSharedPlaybackState(),
                    this._serverPlaybackState
                );

                // ON transitions: enqueue immediate playback for newly active keys (new activation or new URL).
                for (const item of pl) {
                    const wasActive = this.sameActivationPersisted(prevPersisted[item.key], item);
                    if (wasActive) continue;
                    if (sharedPlayback && this.samePlaybackActivation(sharedPlayback, item)) continue;

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

                const nextPersisted = {};
                for (const item of pl) {
                    nextPersisted[item.key] = {
                        url: this.normalizeAudioUrl(item.url),
                        activationId: item.activationId == null ? null : String(item.activationId)
                    };
                }
                this.writePersistedActivationSnapshot(nextPersisted);

                // Deterministic ordering: by bit then key.
                if (this._playQueue.length) {
                    this._playQueue.sort((a, b) => a.bit - b.bit || String(a.key).localeCompare(String(b.key)));
                }
                if (sharedPlayback) {
                    const sharedItem = activeByKey[sharedPlayback.key];
                    if (
                        sharedItem &&
                        (
                            this.sameActivationPersisted(prevPersisted[sharedItem.key], sharedItem) ||
                            this.samePlaybackActivation(sharedPlayback, sharedItem)
                        )
                    ) {
                        this.maybeJoinFromPlaybackState(sharedPlayback);
                    }
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
                    if (j.mode) {
                        this.mode = j.mode;
                    }

                    if (typeof j.alertRepeatIntervalMinutes === 'number' && j.alertRepeatIntervalMinutes > 0) {
                        this.repeatEveryMs = j.alertRepeatIntervalMinutes * 60 * 1000;
                    }
                    if (typeof j.alertMaxRepeats === 'number' && j.alertMaxRepeats >= 0) {
                        this.maxAlertRepeats = j.alertMaxRepeats;
                    }
                    if (typeof j.serverTimeMs === 'number' && Number.isFinite(j.serverTimeMs)) {
                        this._serverTimeSkewMs = j.serverTimeMs - Date.now();
                    }
                    this._serverPlaybackState = this.normalizePlaybackState(j.playbackSync);

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
