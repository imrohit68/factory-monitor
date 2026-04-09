package com.example.system.dto;

/**
 * How to update one workstation slot's alert audio when saving the admin form. When
 * {@code touchOriginalName} is false, the slot's display name in the database is left unchanged.
 */
public record SlotAudioPatch(String audioPath, String audioOriginalName, boolean touchOriginalName) {}
