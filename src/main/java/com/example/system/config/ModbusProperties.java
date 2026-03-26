package com.example.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Modbus RTU (serial) settings for input reads and relay writes.
 */
@ConfigurationProperties(prefix = "system.modbus")
@Getter
@Setter
public class ModbusProperties {

    /**
     * {@code system.modbus.input-slave-id}.
     */
    private int inputSlaveId = 1;

    /** Relays per output board (e.g. Waveshare 32CH); validates relay numbers on FC05. */
    private int relayChannelsPerSlave = 32;

    private String portName = "cu.usbserial-B002EZIO";
    private int baudRate = 9600;
    private int dataBits = 8;
    private int stopBits = 1;
    private String parity = "None";
    private String encoding = "rtu";
    private int inputRegisterStart = 0;
    private int inputRegisterCount = 4;
    private int pollIntervalMs = 5000;
    private boolean logEachRead = true;
    private int coilStartAddress = 0;
}
