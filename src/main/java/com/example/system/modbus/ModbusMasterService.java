package com.example.system.modbus;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.system.config.ModbusProperties;
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.util.SerialParameters;

import jakarta.annotation.PreDestroy;

/**
 * FC04 input register read from {@link ModbusProperties#getInputSlaveId()}, and FC05 coil writes to
 * per-call output slave IDs (e.g. Waveshare Modbus RTU Relay 32CH per
 * <a href="https://www.waveshare.com/wiki/Modbus_RTU_Relay_32CH">Waveshare wiki</a>).
 */
@Service
public class ModbusMasterService {

    private static final Logger log = LoggerFactory.getLogger(ModbusMasterService.class);

    private enum UserErrorContext {
        OPEN_PORT,
        READ_INPUTS,
        WRITE_OUTPUTS
    }

    private final ModbusProperties props;
    private final ReentrantLock lock = new ReentrantLock();

    private ModbusSerialMaster master;
    private volatile boolean connected;
    private volatile String lastError;

    public ModbusMasterService(ModbusProperties props) {
        this.props = props;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getLastError() {
        return lastError;
    }

    public void ensureConnected() {
        lock.lock();
        try {
            if (connected && master != null) {
                return;
            }
            disconnectUnlocked();
            SerialParameters params = new SerialParameters();
            params.setPortName(normalizePort(props.getPortName()));
            params.setBaudRate(props.getBaudRate());
            params.setDatabits(props.getDataBits());
            params.setParity(props.getParity());
            params.setStopbits(props.getStopBits());
            params.setEncoding(props.getEncoding());
            master = new ModbusSerialMaster(params);
            master.connect();
            connected = true;
            lastError = null;
            log.info("Modbus serial connected on {}", props.getPortName());
        } catch (Exception e) {
            lastError = toUserFacingMessage(e, UserErrorContext.OPEN_PORT);
            log.warn("Modbus connect failed: {}", e.getMessage(), e);
            disconnectUnlocked();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reads input registers from {@link ModbusProperties#getInputSlaveId()} only.
     */
    public boolean[] readInputBits(int bitCount) {
        ensureConnected();
        if (!connected || master == null) {
            if (props.isLogEachRead()) {
                log.debug("Modbus read skipped (not connected); {} bits unavailable", bitCount);
            }
            return new boolean[bitCount];
        }
        lock.lock();
        try {
            int regStart = props.getInputRegisterStart();
            int regCount = props.getInputRegisterCount();
            int inputSlave = props.getInputSlaveId();
            InputRegister[] registers = master.readInputRegisters(inputSlave, regStart, regCount);
            boolean[] bits = registersToBits(registers, bitCount);
            if (props.isLogEachRead()) {
                logSuccessfulRead(inputSlave, regStart, regCount, registers, bits);
            }
            return bits;
        } catch (Exception e) {
            lastError = toUserFacingMessage(e, UserErrorContext.READ_INPUTS);
            log.warn(
                    "Modbus FC04 read failed (inputSlave={}): {}",
                    props.getInputSlaveId(),
                    e.getMessage(),
                    e);
            connected = false;
            try {
                if (master != null) {
                    master.disconnect();
                }
            } catch (Exception ignored) {
                // ignore
            }
            master = null;
            return new boolean[bitCount];
        } finally {
            lock.unlock();
        }
    }

    private void logSuccessfulRead(
            int inputSlave, int regStart, int regCount, InputRegister[] registers, boolean[] bits) {
        StringBuilder raw = new StringBuilder();
        if (registers != null) {
            for (int i = 0; i < registers.length; i++) {
                raw.append(String.format("reg[%d]=0x%04X ", regStart + i, registers[i].getValue()));
            }
        }
        log.info(
                "Modbus INPUT read FC04 slave={} start={} count={} rawRegisters: {}",
                inputSlave,
                regStart,
                regCount,
                raw);
        log.info(
                "Modbus INPUT decoded bits ON (indices 0..{}): {}",
                bits.length - 1,
                formatTrueBitIndices(bits));
    }

    static String formatTrueBitIndices(boolean[] bits) {
        StringBuilder s = new StringBuilder();
        int n = 0;
        for (int i = 0; i < bits.length; i++) {
            if (bits[i]) {
                if (n++ > 0) {
                    s.append(',');
                }
                s.append(i);
            }
        }
        return n == 0 ? "(none)" : s.toString();
    }

    /**
     * FC05 write single coil on the given relay slave. {@code relayNumber} is 1-based, up to
     * {@link ModbusProperties#getRelayChannelsPerSlave()}; coil index = relayNumber − 1, so coil address =
     * {@code coilStartAddress + (relayNumber - 1)}.
     */
    public void writeRelayOutput(int outputSlaveId, int relayNumber, boolean energized) {
        writeRelayOutput(outputSlaveId, relayNumber, energized, false);
    }

    /**
     * @param quietSuccessLog when true, log successful writes at DEBUG (for per-poll input/output sync).
     */
    public void writeRelayOutput(int outputSlaveId, int relayNumber, boolean energized, boolean quietSuccessLog) {
        int channels = props.getRelayChannelsPerSlave();
        if (relayNumber < 1 || relayNumber > channels) {
            log.warn(
                    "Modbus OUTPUT skipped (invalid relay): slave={} relayNumber={} (valid 1–{}) energized={}",
                    outputSlaveId,
                    relayNumber,
                    channels,
                    energized);
            return;
        }
        int coilIndex = relayNumber - 1;
        ensureConnected();
        if (!connected || master == null) {
            log.warn(
                    "Modbus OUTPUT skipped (not connected): slave={} relay={} energized={}",
                    outputSlaveId,
                    relayNumber,
                    energized);
            return;
        }
        lock.lock();
        try {
            int coilAddress = props.getCoilStartAddress() + coilIndex;
            master.writeCoil(outputSlaveId, coilAddress, energized);
            if (quietSuccessLog) {
                log.debug(
                        "Modbus OUTPUT FC05 slave={} relay={} coilIndex={} coilAddress=0x{} (start={}+idx) energized={}",
                        outputSlaveId,
                        relayNumber,
                        coilIndex,
                        String.format("%04X", coilAddress & 0xFFFF),
                        props.getCoilStartAddress(),
                        energized);
            } else {
                log.info(
                        "Modbus OUTPUT FC05 slave={} relay={} coilIndex={} coilAddress=0x{} (start={}+idx) energized={}",
                        outputSlaveId,
                        relayNumber,
                        coilIndex,
                        String.format("%04X", coilAddress & 0xFFFF),
                        props.getCoilStartAddress(),
                        energized);
            }
        } catch (Exception e) {
            lastError = toUserFacingMessage(e, UserErrorContext.WRITE_OUTPUTS);
            log.warn(
                    "Modbus OUTPUT FC05 failed slave={} relay={}: {}",
                    outputSlaveId,
                    relayNumber,
                    e.getMessage(),
                    e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates the serial port, disconnects, and attempts a new connection (used after saving device settings).
     */
    public void reconnect(String newPortName) {
        lock.lock();
        try {
            props.setPortName(newPortName);
            disconnectUnlocked();
            lastError = null;
        } finally {
            lock.unlock();
        }
        ensureConnected();
    }

    @PreDestroy
    public void shutdown() {
        lock.lock();
        try {
            disconnectUnlocked();
        } finally {
            lock.unlock();
        }
    }

    private void disconnectUnlocked() {
        connected = false;
        if (master != null) {
            try {
                master.disconnect();
            } catch (Exception ignored) {
                // ignore
            }
            master = null;
        }
    }

    /**
     * Short message for the dashboard and device screen. Full detail stays in logs.
     */
    static String toUserFacingMessage(Throwable e, UserErrorContext ctx) {
        if (e == null) {
            return fallbackForContext(ctx);
        }
        String raw = e.getMessage();
        String lower = raw != null ? raw.toLowerCase(Locale.ROOT) : "";

        if (lower.contains("invalid port descriptor")
                || lower.contains("invalid port")
                || lower.contains("could not open port")
                || lower.contains("port not found")
                || lower.contains("unknown port")
                || (lower.contains("no such file") && lower.contains("dev"))
                || lower.contains("gnu.io.portinuseexception")
                || (lower.contains("jssc") && lower.contains("port"))) {
            return "Unable to open the serial port. Check the USB connection and select the correct port under Configure device.";
        }
        if (lower.contains("permission denied")
                || lower.contains("access denied")
                || lower.contains("operation not permitted")) {
            return "Unable to access the serial port. Close other apps using this device or check port permissions.";
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("time out")) {
            return "Unable to read inputs: the device did not respond in time. Check wiring, power, and Modbus settings.";
        }
        if (lower.contains("broken pipe")
                || lower.contains("not connected")
                || lower.contains("connection reset")
                || lower.contains("i/o error")
                || lower.contains("io error")) {
            return fallbackForContext(ctx);
        }
        if (e instanceof IOException) {
            return fallbackForContext(ctx);
        }
        if (lower.contains("modbus")
                || lower.contains("slave")
                || lower.contains("exception response")
                || lower.contains("illegal data address")) {
            return ctx == UserErrorContext.WRITE_OUTPUTS
                    ? "Unable to update relay outputs. Check Modbus wiring, slave ID, and relay addresses."
                    : "Unable to read inputs from the device. Check Modbus wiring, slave ID, and register settings.";
        }

        return fallbackForContext(ctx);
    }

    private static String fallbackForContext(UserErrorContext ctx) {
        return switch (ctx) {
            case OPEN_PORT ->
                    "Unable to connect to the serial device. Check the USB connection and port under Configure device.";
            case READ_INPUTS -> "Unable to read inputs from the connected device. Check the connection and settings.";
            case WRITE_OUTPUTS ->
                    "Unable to update relay outputs. Check the device connection and Modbus settings.";
        };
    }

    static String normalizePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String p = raw.trim();
        if (p.startsWith("/dev/")) {
            return p.substring(5);
        }
        return p;
    }

    static boolean[] registersToBits(InputRegister[] registers, int bitCount) {
        boolean[] bits = new boolean[bitCount];
        if (registers == null) {
            return bits;
        }
        for (int reg = 0; reg < registers.length; reg++) {
            int value = registers[reg].getValue();
            for (int bit = 0; bit < 16; bit++) {
                int inputNumber = reg * 16 + bit;
                if (inputNumber >= bitCount) {
                    return bits;
                }
                bits[inputNumber] = (value & (1 << bit)) != 0;
            }
        }
        return bits;
    }
}
