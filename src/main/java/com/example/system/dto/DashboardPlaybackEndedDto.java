package com.example.system.dto;

/** Client reports alert clip finished so other tabs can skip re-playing the same ON episode until repeat interval. */
public record DashboardPlaybackEndedDto(String key, String activationId) {}
