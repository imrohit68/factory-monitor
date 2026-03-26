package com.example.system.domain;

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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "workstation_slot",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_ws_slot_input_bit", columnNames = "input_bit_index"),
            @UniqueConstraint(name = "uk_ws_slot_output", columnNames = {"output_slave_id", "output_channel"}),
            @UniqueConstraint(name = "uk_ws_slot_ws_role", columnNames = {"workstation_id", "role"})
        })
@Getter
@Setter
public class WorkstationSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER)
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

}
