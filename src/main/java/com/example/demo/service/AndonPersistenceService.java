package com.example.demo.service;

import com.example.demo.domain.AndonEvent;
import com.example.demo.domain.AndonEventStatus;
import com.example.demo.repository.AndonEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AndonPersistenceService {

    private final AndonEventRepository events;

    public AndonPersistenceService(AndonEventRepository events) {
        this.events = events;
    }

    @Transactional
    public Long openEvent(long mappingId, int inputBitIndex, int outputSlaveId, int outputChannel) {
        AndonEvent e = new AndonEvent();
        e.setMappingId(mappingId);
        e.setInputBitIndex(inputBitIndex);
        e.setOutputSlaveId(outputSlaveId);
        e.setOutputChannel(outputChannel);
        e.setStatus(AndonEventStatus.OPEN);
        e.setEventTime(Instant.now());
        return events.save(e).getId();
    }

    /**
     * Append-only: records a close as a new row. The original OPEN row is unchanged.
     */
    @Transactional
    public Long insertCloseEvent(long mappingId, int inputBitIndex, int outputSlaveId, int outputChannel) {
        AndonEvent e = new AndonEvent();
        e.setMappingId(mappingId);
        e.setInputBitIndex(inputBitIndex);
        e.setOutputSlaveId(outputSlaveId);
        e.setOutputChannel(outputChannel);
        e.setStatus(AndonEventStatus.CLOSED);
        e.setEventTime(Instant.now());
        return events.save(e).getId();
    }
}
