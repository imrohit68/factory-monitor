package com.example.system.dto;

import com.example.system.domain.WorkstationRole;

public record DashboardSlotDto(
        long slotId,
        WorkstationRole role,
        int inputBitIndex,
        int outputSlaveId,
        int outputChannel,
        boolean active,
        /** Present while {@code active}; identifies this physical ON episode for dashboard clients. */
        Long openEventId,
        String audioUrl) {}
