package com.example.system.dto;

import java.time.LocalDate;

/** Defaults for the admin event log / CSV export date range UI. */
public record EventLogPageData(LocalDate defaultStart, LocalDate defaultEnd, int maxRangeDays) {}
