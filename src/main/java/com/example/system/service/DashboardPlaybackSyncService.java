package com.example.system.service;

import com.example.system.dto.DashboardPlaybackEndedDto;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ephemeral dashboard alert coordination: clip-end markers for new-tab dedupe, and monotonic replay epochs
 * advanced on the server clock (no per-client playback heartbeats).
 */
@Service
public class DashboardPlaybackSyncService {

    private final Map<String, ClipEndMarker> lastClipEndBySlotKey = new ConcurrentHashMap<>();

    /** Monotonic per slot key — clients consume via GET to schedule repeat plays in sync. */
    private final Map<String, AtomicInteger> replayEpochBySlotKey = new ConcurrentHashMap<>();

    /**
     * After a repeat was logically fired for {@code endedAtMs}, we store that value so the same window is not
     * advanced again on every poll.
     */
    private final Map<String, Long> repeatEpochAdvancedForEndedAtMs = new ConcurrentHashMap<>();

    private record ClipEndMarker(String activationId, long endedAtMs) {}

    public void recordClipEnded(DashboardPlaybackEndedDto dto) {
        if (dto == null) {
            return;
        }
        String key = normalize(dto.key());
        String activationId = normalize(dto.activationId());
        if (key == null || activationId == null) {
            return;
        }
        long endedAt = System.currentTimeMillis();
        lastClipEndBySlotKey.put(key, new ClipEndMarker(activationId, endedAt));
        repeatEpochAdvancedForEndedAtMs.remove(key);
    }

    /** Drop markers for slots that are no longer active with audio. */
    public void pruneInactiveMarkers(Set<String> activeAudioSlotKeys) {
        if (activeAudioSlotKeys == null || activeAudioSlotKeys.isEmpty()) {
            lastClipEndBySlotKey.keySet().removeIf(k -> true);
            replayEpochBySlotKey.keySet().removeIf(k -> true);
            repeatEpochAdvancedForEndedAtMs.keySet().removeIf(k -> true);
            return;
        }
        lastClipEndBySlotKey.keySet().removeIf(k -> !activeAudioSlotKeys.contains(k));
        replayEpochBySlotKey.keySet().removeIf(k -> !activeAudioSlotKeys.contains(k));
        repeatEpochAdvancedForEndedAtMs.keySet().removeIf(k -> !activeAudioSlotKeys.contains(k));
    }

    /**
     * For each active slot with a clip-end marker, if the repeat interval has passed since that end, bump the
     * replay epoch at most once per distinct {@code endedAtMs} value.
     */
    /**
     * Advances replay epochs where the repeat interval has elapsed; returns keys whose epoch was bumped on this
     * call (at most once per distinct {@code endedAtMs} per key).
     */
    public Set<String> tickReplayEpochs(Set<String> activeAudioSlotKeys, long nowMs, long repeatMs) {
        Set<String> advanced = new HashSet<>();
        if (activeAudioSlotKeys == null || repeatMs <= 0) {
            return advanced;
        }
        for (String key : activeAudioSlotKeys) {
            if (key == null) {
                continue;
            }
            ClipEndMarker marker = lastClipEndBySlotKey.get(key);
            if (marker == null) {
                continue;
            }
            long endedAt = marker.endedAtMs();
            if (nowMs < endedAt + repeatMs) {
                continue;
            }
            Long advancedFor = repeatEpochAdvancedForEndedAtMs.get(key);
            if (advancedFor != null && advancedFor == endedAt) {
                continue;
            }
            replayEpochBySlotKey.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
            repeatEpochAdvancedForEndedAtMs.put(key, endedAt);
            advanced.add(key);
        }
        return advanced;
    }

    public Map<String, Integer> alertReplayEpochSnapshot() {
        Map<String, Integer> out = new HashMap<>();
        for (var e : replayEpochBySlotKey.entrySet()) {
            out.put(e.getKey(), e.getValue().get());
        }
        return out;
    }

    /** For {@code GET /api/dashboard} — nested maps serialize cleanly to JSON. */
    public Map<String, Map<String, Object>> lastClipEndSnapshot() {
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (var e : lastClipEndBySlotKey.entrySet()) {
            ClipEndMarker m = e.getValue();
            if (m == null) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("activationId", m.activationId());
            row.put("endedAtMs", m.endedAtMs());
            out.put(e.getKey(), row);
        }
        return out;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
