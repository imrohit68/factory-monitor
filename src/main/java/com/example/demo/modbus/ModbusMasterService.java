package com.example.demo.modbus;

import com.example.demo.config.ModbusProperties;
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.util.SerialParameters;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FC04 input register read from fixed {@link ModbusConstants#INPUT_SLAVE_ID}, and FC05 coil writes to
 * per-call output slave IDs (e.g. Waveshare Modbus RTU Relay 32CH per
 * <a href="https://www.waveshare.com/wiki/Modbus_RTU_Relay_32CH">Waveshare wiki</a>).
 */
@Service
public class ModbusMasterService {

    private static final Logger log = LoggerFactory.getLogger(ModbusMasterService.class);

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
            lastError = e.getMessage();
            log.warn("Modbus connect failed: {}", e.getMessage());
            disconnectUnlocked();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reads input registers from {@link ModbusConstants#INPUT_SLAVE_ID} only (fixed in code).
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
            int inputSlave = ModbusConstants.INPUT_SLAVE_ID;
            InputRegister[] registers = master.readInputRegisters(inputSlave, regStart, regCount);
            boolean[] bits = registersToBits(registers, bitCount);
            if (props.isLogEachRead()) {
                logSuccessfulRead(inputSlave, regStart, regCount, registers, bits);
            }
            return bits;
        } catch (Exception e) {
            lastError = e.getMessage();
            log.warn(
                    "Modbus FC04 read failed (inputSlave={} fixed): {}",
                    ModbusConstants.INPUT_SLAVE_ID,
                    e.getMessage());
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
                "Modbus INPUT read FC04 slave={} (fixed) start={} count={} rawRegisters: {}",
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
     * FC05 write single coil on the given relay slave. {@code relayNumber} is 1-based (relay 1…32 on the
     * board); coil index = relayNumber − 1, so coil address = {@code coilStartAddress + (relayNumber - 1)}.
     */
    public void writeRelayOutput(int outputSlaveId, int relayNumber, boolean energized) {
        if (relayNumber < 1 || relayNumber > ModbusConstants.RELAY_CHANNELS_PER_SLAVE) {
            log.warn(
                    "Modbus OUTPUT skipped (invalid relay): slave={} relayNumber={} (valid 1–{}) energized={}",
                    outputSlaveId,
                    relayNumber,
                    ModbusConstants.RELAY_CHANNELS_PER_SLAVE,
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
            log.info(
                    "Modbus OUTPUT FC05 slave={} relay={} coilIndex={} coilAddress=0x{} (start={}+idx) energized={}",
                    outputSlaveId,
                    relayNumber,
                    coilIndex,
                    String.format("%04X", coilAddress & 0xFFFF),
                    props.getCoilStartAddress(),
                    energized);
        } catch (Exception e) {
            lastError = e.getMessage();
            log.warn(
                    "Modbus OUTPUT FC05 failed slave={} relay={}: {}",
                    outputSlaveId,
                    relayNumber,
                    e.getMessage());
        } finally {
            lock.unlock();
        }
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
        Arrays.fill(bits, false);
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
