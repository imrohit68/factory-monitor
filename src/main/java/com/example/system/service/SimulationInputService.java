package com.example.system.service;

import com.example.system.domain.WorkstationSlot;
import com.example.system.repository.WorkstationSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SimulationInputService {

    private final WorkstationSlotRepository slotRepository;
    private final ConcurrentMap<Integer, Boolean> inputStateByBit = new ConcurrentHashMap<>();

    public SimulationInputService(WorkstationSlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public boolean[] readBits(int bitCount) {
        boolean[] bits = new boolean[Math.max(bitCount, 0)];
        inputStateByBit.forEach(
                (bitIndex, energized) -> {
                    if (!Boolean.TRUE.equals(energized)) {
                        return;
                    }
                    if (bitIndex == null || bitIndex < 0 || bitIndex >= bits.length) {
                        return;
                    }
                    bits[bitIndex] = true;
                });
        return bits;
    }

    @Transactional(readOnly = true)
    public void toggleBitForSlot(long slotId) {
        WorkstationSlot slot =
                slotRepository
                        .findById(slotId)
                        .orElseThrow(() -> new IllegalArgumentException("Workstation slot not found: " + slotId));
        int bitIndex = slot.getInputBitIndex();
        if (bitIndex < 0) {
            throw new IllegalArgumentException("Workstation slot has no valid input bit: " + slotId);
        }
        inputStateByBit.compute(bitIndex, (key, current) -> Boolean.TRUE.equals(current) ? Boolean.FALSE : Boolean.TRUE);
    }
}
