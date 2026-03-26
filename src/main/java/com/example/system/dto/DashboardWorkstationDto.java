package com.example.system.dto;

import java.util.List;

/** Audio is per slot (channel); see {@link DashboardSlotDto#audioUrl()}. */
public record DashboardWorkstationDto(long id, String name, List<DashboardSlotDto> slots) {}
