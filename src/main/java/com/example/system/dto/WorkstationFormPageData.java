package com.example.system.dto;

import com.example.system.domain.Workstation;

import java.util.List;

/** Attributes needed to render the workstation admin form (limits, placement options). */
public record WorkstationFormPageData(
        int maxInputBit,
        int maxRelayPerSlave,
        List<Workstation> placementOptions,
        boolean showPlacementControl,
        Long selectedPlaceAfterId) {}
