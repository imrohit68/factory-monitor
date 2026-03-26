package com.example.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "event_log")
@Getter
@Setter
public class EventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER)
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
    private EventStatus status = EventStatus.OPEN;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;
}
