package com.example.system.modbus;

/**
 * Outcome of an FC05 relay write. {@code transmitted} is true only when {@code writeCoil} was invoked.
 */
public record ModbusRelayWriteResult(boolean transmitted, boolean responseOk) {}
