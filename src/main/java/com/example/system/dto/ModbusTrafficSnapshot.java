package com.example.system.dto;

/**
 * Last completed orchestration poll, for dashboard Modbus TX/RX indicators.
 */
public record ModbusTrafficSnapshot(long pollCompletedAtMs, boolean readTx, boolean readRx, boolean writeTx, boolean writeRx) {

    public static ModbusTrafficSnapshot initial() {
        return new ModbusTrafficSnapshot(0L, false, false, false, false);
    }

    public static ModbusTrafficSnapshot idleNow() {
        long t = System.currentTimeMillis();
        return new ModbusTrafficSnapshot(t, false, false, false, false);
    }
}
