package com.example.demo.dto;

import java.util.List;

/** Audio is per slot (channel); see {@link DashboardSlotDto#audioUrl()}. */
public record DashboardWorkstationDto(long id, String name, List<DashboardSlotDto> slots) {}
