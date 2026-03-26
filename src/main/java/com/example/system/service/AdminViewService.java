package com.example.system.service;

import com.example.system.config.AppProperties;
import com.example.system.dto.EventLogPageData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AdminViewService {

    private final AppProperties appProperties;

    public EventLogPageData eventLogPageData() {
        LocalDate today = LocalDate.now();
        return new EventLogPageData(today.minusDays(6), today, appProperties.getLogRetentionDays());
    }
}
