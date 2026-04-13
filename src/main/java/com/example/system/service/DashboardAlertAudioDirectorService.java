package com.example.system.service;

import com.example.system.config.AppProperties;
import com.example.system.dto.DashboardActiveAlertSlot;
import com.example.system.dto.DashboardPlaybackEndedDto;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for which dashboard alert clip (if any) clients should play. The browser only
 * executes {@code alertAudio} from {@code GET /api/dashboard} and reports clip end via POST.
 */
@Service
public class DashboardAlertAudioDirectorService {

    private static final String ACTION_PLAY = "PLAY";
    private static final String ACTION_IDLE = "IDLE";
    private static final String REASON_INITIAL = "INITIAL";
    private static final String REASON_REPEAT = "REPEAT";

    private final DashboardPlaybackSyncService playbackSync;
    private final AppProperties appProperties;

    private final Object lock = new Object();

    private long nextPlaySequence = 1L;

    /** Head-of-line play currently expected to be audible until a matching clip-ended POST. */
    private InFlight inFlight;

    private final Deque<QueuedPlay> queue = new ArrayDeque<>();

    /** Do not issue the next queued clip until this time (server millis). */
    private long blockedUntilMs;

    /** INITIAL already enqueued (or in flight) for this slot key and activation id. */
    private final Map<String, String> initialServedActivationByKey = new ConcurrentHashMap<>();

    /** REPEAT plays enqueued from replay epochs since this activation became active (reset when key inactive). */
    private final Map<String, Integer> repeatEnqueuesByKey = new ConcurrentHashMap<>();

    public DashboardAlertAudioDirectorService(
            DashboardPlaybackSyncService playbackSync, AppProperties appProperties) {
        this.playbackSync = playbackSync;
        this.appProperties = appProperties;
    }

    public void onClipEnded(DashboardPlaybackEndedDto dto) {
        if (dto == null) {
            return;
        }
        String key = normalizeKey(dto.key());
        String activationId = normalizeActivation(dto.activationId());
        if (key == null || activationId == null) {
            return;
        }
        synchronized (lock) {
            playbackSync.recordClipEnded(dto);
            if (inFlight != null
                    && key.equals(inFlight.slotKey())
                    && activationId.equals(inFlight.activationId())) {
                inFlight = null;
            }
            if (!queue.isEmpty()) {
                blockedUntilMs = System.currentTimeMillis() + Math.max(0, appProperties.getDashboardAlertGapMs());
            } else {
                blockedUntilMs = 0L;
            }
        }
    }

    /**
     * Reconciles Modbus-derived active slots with the wait queue and returns the instruction payload for JSON.
     */
    public Map<String, Object> reconcileAndBuildInstruction(List<DashboardActiveAlertSlot> activeSlots, long nowMs) {
        List<DashboardActiveAlertSlot> sorted = new ArrayList<>(activeSlots != null ? activeSlots : List.of());
        sorted.sort(
                Comparator.comparingInt(DashboardActiveAlertSlot::inputBitIndex)
                        .thenComparing(DashboardActiveAlertSlot::slotKey));

        Set<String> activeKeys = new HashSet<>();
        Map<String, DashboardActiveAlertSlot> byKey = new HashMap<>();
        for (DashboardActiveAlertSlot s : sorted) {
            if (s == null || s.slotKey() == null) {
                continue;
            }
            activeKeys.add(s.slotKey());
            byKey.put(s.slotKey(), s);
        }

        synchronized (lock) {
            playbackSync.pruneInactiveMarkers(activeKeys);
            long repeatMs = (long) appProperties.getDashboardAlertRepeatIntervalMinutes() * 60_000L;
            Set<String> epochAdvanced = playbackSync.tickReplayEpochs(activeKeys, nowMs, repeatMs);

            for (String k : new HashSet<>(initialServedActivationByKey.keySet())) {
                if (!activeKeys.contains(k)) {
                    initialServedActivationByKey.remove(k);
                }
            }
            for (String k : new HashSet<>(repeatEnqueuesByKey.keySet())) {
                if (!activeKeys.contains(k)) {
                    repeatEnqueuesByKey.remove(k);
                }
            }

            pruneQueueForInactive(activeKeys);
            if (inFlight != null && !activeKeys.contains(inFlight.slotKey())) {
                inFlight = null;
            }

            int maxRepeats = Math.max(0, appProperties.getDashboardAlertMaxRepeats());

            for (DashboardActiveAlertSlot slot : sorted) {
                maybeEnqueueInitial(slot, nowMs, repeatMs);
            }

            for (String key : epochAdvanced) {
                DashboardActiveAlertSlot slot = byKey.get(key);
                if (slot == null) {
                    continue;
                }
                maybeEnqueueRepeat(slot, maxRepeats);
            }

            sortQueue();

            return materializeInstruction(nowMs);
        }
    }

    private void maybeEnqueueInitial(DashboardActiveAlertSlot slot, long nowMs, long repeatMs) {
        String key = slot.slotKey();
        String act = slot.activationId();
        if (act == null || act.isEmpty()) {
            return;
        }
        if (Objects.equals(initialServedActivationByKey.get(key), act)) {
            return;
        }
        if (inCooldownForInitial(key, act, nowMs, repeatMs)) {
            return;
        }
        if (queueContainsInitialFor(key, act)) {
            initialServedActivationByKey.put(key, act);
            return;
        }
        if (inFlight != null && key.equals(inFlight.slotKey()) && act.equals(inFlight.activationId())) {
            initialServedActivationByKey.put(key, act);
            return;
        }
        queue.addLast(new QueuedPlay(REASON_INITIAL, key, slot.audioUrl(), act, slot.inputBitIndex()));
        initialServedActivationByKey.put(key, act);
    }

    private void maybeEnqueueRepeat(DashboardActiveAlertSlot slot, int maxRepeats) {
        String key = slot.slotKey();
        int prev = repeatEnqueuesByKey.getOrDefault(key, 0);
        if (prev >= maxRepeats) {
            return;
        }
        String act = slot.activationId() != null ? slot.activationId() : "";
        if (queueContainsRepeatForKey(key)) {
            return;
        }
        queue.addLast(new QueuedPlay(REASON_REPEAT, key, slot.audioUrl(), act, slot.inputBitIndex()));
        repeatEnqueuesByKey.put(key, prev + 1);
    }

    private boolean inCooldownForInitial(String key, String activationId, long nowMs, long repeatMs) {
        Map<String, Map<String, Object>> snap = playbackSync.lastClipEndSnapshot();
        Map<String, Object> row = snap != null ? snap.get(key) : null;
        if (row == null) {
            return false;
        }
        Object aid = row.get("activationId");
        if (aid == null || !activationId.equals(String.valueOf(aid))) {
            return false;
        }
        Object ended = row.get("endedAtMs");
        if (!(ended instanceof Number n)) {
            return false;
        }
        return nowMs < n.longValue() + repeatMs;
    }

    private void pruneQueueForInactive(Set<String> activeKeys) {
        for (Iterator<QueuedPlay> it = queue.iterator(); it.hasNext(); ) {
            QueuedPlay q = it.next();
            if (!activeKeys.contains(q.slotKey())) {
                if (REASON_INITIAL.equals(q.reason())) {
                    String act = q.activationId();
                    if (act != null && Objects.equals(initialServedActivationByKey.get(q.slotKey()), act)) {
                        initialServedActivationByKey.remove(q.slotKey());
                    }
                }
                if (REASON_REPEAT.equals(q.reason())) {
                    String sk = q.slotKey();
                    repeatEnqueuesByKey.computeIfPresent(sk, (k, v) -> {
                        int next = v - 1;
                        return next <= 0 ? null : next;
                    });
                }
                it.remove();
            }
        }
    }

    private boolean queueContainsInitialFor(String key, String activationId) {
        for (QueuedPlay q : queue) {
            if (REASON_INITIAL.equals(q.reason()) && key.equals(q.slotKey()) && activationId.equals(q.activationId())) {
                return true;
            }
        }
        return false;
    }

    private boolean queueContainsRepeatForKey(String key) {
        for (QueuedPlay q : queue) {
            if (REASON_REPEAT.equals(q.reason()) && key.equals(q.slotKey())) {
                return true;
            }
        }
        return false;
    }

    private void sortQueue() {
        List<QueuedPlay> list = new ArrayList<>(queue);
        list.sort(
                Comparator.comparingInt(QueuedPlay::inputBitIndex)
                        .thenComparing(QueuedPlay::slotKey));
        queue.clear();
        for (QueuedPlay q : list) {
            queue.addLast(q);
        }
    }

    private Map<String, Object> materializeInstruction(long nowMs) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (inFlight != null) {
            out.put("sequence", inFlight.sequence());
            out.put("action", ACTION_PLAY);
            out.put("slotKey", inFlight.slotKey());
            out.put("audioUrl", inFlight.audioUrl());
            out.put("activationId", inFlight.activationId());
            out.put("inputBitIndex", inFlight.inputBitIndex());
            out.put("reason", inFlight.reason());
            return out;
        }
        if (nowMs < blockedUntilMs) {
            out.put("sequence", nextPlaySequence);
            out.put("action", ACTION_IDLE);
            out.put("waitingAfterClipGap", true);
            return out;
        }
        QueuedPlay next = queue.pollFirst();
        if (next == null) {
            out.put("sequence", nextPlaySequence);
            out.put("action", ACTION_IDLE);
            out.put("waitingAfterClipGap", false);
            return out;
        }
        long seq = nextPlaySequence++;
        inFlight = new InFlight(
                next.slotKey(), next.audioUrl(), next.activationId(), next.inputBitIndex(), seq, next.reason());
        out.put("sequence", seq);
        out.put("action", ACTION_PLAY);
        out.put("slotKey", inFlight.slotKey());
        out.put("audioUrl", inFlight.audioUrl());
        out.put("activationId", inFlight.activationId());
        out.put("inputBitIndex", inFlight.inputBitIndex());
        out.put("reason", inFlight.reason());
        return out;
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        String t = key.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeActivation(String activationId) {
        if (activationId == null) {
            return null;
        }
        String t = activationId.trim();
        return t.isEmpty() ? null : t;
    }

    private record QueuedPlay(String reason, String slotKey, String audioUrl, String activationId, int inputBitIndex) {}

    private record InFlight(
            String slotKey,
            String audioUrl,
            String activationId,
            int inputBitIndex,
            long sequence,
            String reason) {}
}
