package com.example.system.dto;

public record DashboardPlaybackSyncDto(
        String senderId,
        String key,
        String url,
        String activationId,
        long wallClockStartMs,
        long updatedAtMs) {}
