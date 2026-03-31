package com.example.system.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Applies {@code system.modbus.port-name} from {@code {data-dir}/device-port.properties} if present
 * (written when an admin saves serial settings from the dashboard).
 */
@Component
@Order(50)
public class DeviceConfigLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeviceConfigLoader.class);
    public static final String PORT_FILE = "device-port.properties";

    private final AppProperties appProperties;
    private final ModbusProperties modbusProperties;

    public DeviceConfigLoader(AppProperties appProperties, ModbusProperties modbusProperties) {
        this.appProperties = appProperties;
        this.modbusProperties = modbusProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path file = Path.of(appProperties.getDataDir()).resolve(PORT_FILE);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            String port = p.getProperty("system.modbus.port-name");
            if (port != null && !port.isBlank()) {
                log.info("Loaded saved serial port from {}: {}", file, port);
                modbusProperties.setPortName(port.trim());
            }
        } catch (Exception e) {
            log.warn("Could not load {}: {}", file, e.getMessage());
        }
    }
}
