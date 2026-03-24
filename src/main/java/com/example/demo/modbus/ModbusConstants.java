package com.example.demo.modbus;

/**
 * Input DI is always read from this Modbus unit (FC04). Configured in code per product wiring.
 */
public final class ModbusConstants {

    /** Fixed input slave — reads {@link com.example.demo.config.ModbusProperties} input registers from this ID. */
    public static final int INPUT_SLAVE_ID = 1;

    /** Waveshare Modbus RTU Relay 32CH: relay numbers 1–32 on the board (maps to coils 0x0000–0x001F). */
    public static final int RELAY_CHANNELS_PER_SLAVE = 32;

    private ModbusConstants() {}
}
