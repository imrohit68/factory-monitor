package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "andon_event_log")
public class AndonEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mapping_id")
    private Long mappingId;

    @Column(name = "input_bit_index", nullable = false)
    private int inputBitIndex;

    @Column(name = "output_slave_id", nullable = false)
    private int outputSlaveId;

    @Column(name = "output_channel", nullable = false)
    private int outputChannel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AndonEventStatus status = AndonEventStatus.OPEN;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    public Long getId() {
        return id;
    }

    public Long getMappingId() {
        return mappingId;
    }

    public void setMappingId(Long mappingId) {
        this.mappingId = mappingId;
    }

    public int getInputBitIndex() {
        return inputBitIndex;
    }

    public void setInputBitIndex(int inputBitIndex) {
        this.inputBitIndex = inputBitIndex;
    }

    public int getOutputSlaveId() {
        return outputSlaveId;
    }

    public void setOutputSlaveId(int outputSlaveId) {
        this.outputSlaveId = outputSlaveId;
    }

    public int getOutputChannel() {
        return outputChannel;
    }

    public void setOutputChannel(int outputChannel) {
        this.outputChannel = outputChannel;
    }

    public AndonEventStatus getStatus() {
        return status;
    }

    public void setStatus(AndonEventStatus status) {
        this.status = status;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }
}
