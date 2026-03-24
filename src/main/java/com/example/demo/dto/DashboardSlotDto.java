package com.example.demo.dto;

import com.example.demo.domain.WorkstationRole;

public record DashboardSlotDto(
        long slotId,
        WorkstationRole role,
        int inputBitIndex,
        int outputSlaveId,
        int outputChannel,
        boolean active,
        String audioUrl) {}
