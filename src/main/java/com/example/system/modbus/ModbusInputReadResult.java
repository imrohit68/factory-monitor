package com.example.system.modbus;

/**
 * Outcome of an FC04 input register read (or skip when not connected).
 */
public record ModbusInputReadResult(boolean[] bits, boolean attempted, boolean responseOk) {}
