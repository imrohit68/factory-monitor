package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "andon.modbus")
public class  ModbusProperties {

    /**
     * Serial device name (e.g. cu.usbserial-B002EZIO or full path /dev/cu.usbserial-B002EZIO).
     */
    private String portName = "cu.usbserial-B002EZIO";

    private int baudRate = 9600;
    private int dataBits = 8;
    private int stopBits = 1;
    /** NONE, EVEN, ODD */
    private String parity = "None";
    /** rtu */
    private String encoding = "rtu";

    /** Function 04 on {@link com.example.demo.modbus.ModbusConstants#INPUT_SLAVE_ID}: starting input register. */
    private int inputRegisterStart = 0;
    /** Number of 16-bit input registers to read. */
    private int inputRegisterCount = 4;

    /** Polling interval for inputs and orchestration (ms). */
    private int pollIntervalMs = 5000;

    /** Log every successful input read (register values + which bits are on). */
    private boolean logEachRead = true;

    /**
     * Base coil address on each relay slave (Waveshare 32CH: 0x0000–0x001F for coil indices 0–31).
     * Absolute coil = coilStartAddress + (relayNumber − 1) where relayNumber is 1–32.
     */
    private int coilStartAddress = 0;

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public int getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate;
    }

    public int getDataBits() {
        return dataBits;
    }

    public void setDataBits(int dataBits) {
        this.dataBits = dataBits;
    }

    public int getStopBits() {
        return stopBits;
    }

    public void setStopBits(int stopBits) {
        this.stopBits = stopBits;
    }

    public String getParity() {
        return parity;
    }

    public void setParity(String parity) {
        this.parity = parity;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public int getInputRegisterStart() {
        return inputRegisterStart;
    }

    public void setInputRegisterStart(int inputRegisterStart) {
        this.inputRegisterStart = inputRegisterStart;
    }

    public int getInputRegisterCount() {
        return inputRegisterCount;
    }

    public void setInputRegisterCount(int inputRegisterCount) {
        this.inputRegisterCount = inputRegisterCount;
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public boolean isLogEachRead() {
        return logEachRead;
    }

    public void setLogEachRead(boolean logEachRead) {
        this.logEachRead = logEachRead;
    }

    public int getCoilStartAddress() {
        return coilStartAddress;
    }

    public void setCoilStartAddress(int coilStartAddress) {
        this.coilStartAddress = coilStartAddress;
    }
}
