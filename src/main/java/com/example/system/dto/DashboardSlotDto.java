package com.example.system.dto;

import com.example.system.domain.WorkstationRole;

public record DashboardSlotDto(
        long slotId,
        WorkstationRole role,
        int inputBitIndex,
        int outputSlaveId,
        int outputChannel,
        boolean active,
        String audioUrl) {}
