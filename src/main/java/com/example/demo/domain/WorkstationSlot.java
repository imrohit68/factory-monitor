package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "workstation_slot",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_ws_slot_input_bit", columnNames = "input_bit_index"),
            @UniqueConstraint(name = "uk_ws_slot_output", columnNames = {"output_slave_id", "output_channel"}),
            @UniqueConstraint(name = "uk_ws_slot_ws_role", columnNames = {"workstation_id", "role"})
        })
public class WorkstationSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workstation_id", nullable = false)
    private Workstation workstation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkstationRole role;

    @Column(name = "input_bit_index", nullable = false)
    private int inputBitIndex;

    @Column(name = "output_slave_id", nullable = false)
    private int outputSlaveId;

    /** Relay number 1–32 on that slave. */
    @Column(name = "output_channel", nullable = false)
    private int outputChannel;

    /** Optional alert sound for this channel only (URL path e.g. /audio/uploads/… or https://…). */
    @Column(name = "audio_path", length = 512)
    private String audioPath;

    public Long getId() {
        return id;
    }

    public Workstation getWorkstation() {
        return workstation;
    }

    public void setWorkstation(Workstation workstation) {
        this.workstation = workstation;
    }

    public WorkstationRole getRole() {
        return role;
    }

    public void setRole(WorkstationRole role) {
        this.role = role;
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

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }
}
