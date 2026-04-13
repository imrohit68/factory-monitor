package com.example.system.dto;

/**
 * One workstation slot that is currently ON and has alert audio configured — input to
 * {@link com.example.system.service.DashboardAlertAudioDirectorService}.
 */
public record DashboardActiveAlertSlot(String slotKey, String audioUrl, String activationId, int inputBitIndex) {}
