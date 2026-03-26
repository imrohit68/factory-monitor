package com.example.system.service;

import com.example.system.domain.EventRecord;
import com.example.system.domain.EventStatus;
import com.example.system.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EventPersistenceService {

    private final EventLogRepository events;

    @Transactional
    public Long openEvent(long mappingId, int inputBitIndex, int outputSlaveId, int outputChannel) {
        EventRecord e = new EventRecord();
        e.setMappingId(mappingId);
        e.setInputBitIndex(inputBitIndex);
        e.setOutputSlaveId(outputSlaveId);
        e.setOutputChannel(outputChannel);
        e.setStatus(EventStatus.OPEN);
        e.setEventTime(Instant.now());
        return events.save(e).getId();
    }

    /**
     * Append-only: records a close as a new row. The original OPEN row is unchanged.
     */
    @Transactional
    public Long insertCloseEvent(long mappingId, int inputBitIndex, int outputSlaveId, int outputChannel) {
        EventRecord e = new EventRecord();
        e.setMappingId(mappingId);
        e.setInputBitIndex(inputBitIndex);
        e.setOutputSlaveId(outputSlaveId);
        e.setOutputChannel(outputChannel);
        e.setStatus(EventStatus.CLOSED);
        e.setEventTime(Instant.now());
        return events.save(e).getId();
    }
}
