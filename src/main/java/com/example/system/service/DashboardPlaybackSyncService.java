package com.example.system.service;

import com.example.system.dto.DashboardPlaybackSyncClearDto;
import com.example.system.dto.DashboardPlaybackSyncDto;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DashboardPlaybackSyncService {

    /** Drop publisher state if no heartbeat (must exceed client poll + network slack). */
    private static final long STALE_MS = 15000;

    private final Map<String, DashboardPlaybackSyncDto> playbackBySenderId = new ConcurrentHashMap<>();

    public DashboardPlaybackSyncDto snapshot() {
        pruneStale();
        return playbackBySenderId.values().stream()
                .max(Comparator.comparingLong(DashboardPlaybackSyncDto::updatedAtMs))
                .orElse(null);
    }

    public void publish(DashboardPlaybackSyncDto state) {
        if (state == null) {
            return;
        }
        String senderId = normalize(state.senderId());
        String key = normalize(state.key());
        String url = canonicalAudioUrlForSync(state.url());
        if (senderId == null || key == null || url == null) {
            return;
        }
        /*
         * Rebasing to the JVM clock keeps freshness checks and join math consistent across
         * desktop WebView vs browser even when client wall clocks differ slightly.
         */
        long serverNow = System.currentTimeMillis();
        long positionMs = Math.max(0L, state.updatedAtMs() - state.wallClockStartMs());
        long rebasedWallStart = serverNow - positionMs;
        DashboardPlaybackSyncDto normalized =
                new DashboardPlaybackSyncDto(
                        senderId,
                        key,
                        url,
                        normalize(state.activationId()),
                        rebasedWallStart,
                        serverNow);
        playbackBySenderId.put(senderId, normalized);
        pruneStale();
    }

    public void clear(DashboardPlaybackSyncClearDto request) {
        if (request == null) {
            return;
        }
        String senderId = normalize(request.senderId());
        if (senderId == null) {
            return;
        }
        playbackBySenderId.remove(senderId);
    }

    private void pruneStale() {
        long cutoff = System.currentTimeMillis() - STALE_MS;
        playbackBySenderId.entrySet().removeIf(e -> e.getValue() == null || e.getValue().updatedAtMs() < cutoff);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Store only path + query so the same file matches across {@code http://127.0.0.1}, {@code localhost},
     * and LAN IPs ({@code http://192.168.x.x}).
     */
    static String canonicalAudioUrlForSync(String url) {
        String trimmed = normalize(url);
        if (trimmed == null) {
            return null;
        }
        try {
            URI u = URI.create(trimmed);
            if (u.isAbsolute()) {
                String path = u.getPath() == null ? "" : u.getPath();
                String query = u.getRawQuery();
                if (query != null && !query.isEmpty()) {
                    return path + "?" + query;
                }
                return path.isEmpty() ? null : path;
            }
            if (!trimmed.startsWith("/")) {
                return "/" + trimmed;
            }
            return trimmed;
        } catch (IllegalArgumentException e) {
            return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        }
    }
}
