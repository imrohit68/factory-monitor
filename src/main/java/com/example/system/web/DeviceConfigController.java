package com.example.system.web;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.system.config.AppProperties;
import com.example.system.config.DeviceConfigLoader;
import com.example.system.config.ModbusProperties;
import com.example.system.modbus.ModbusMasterService;
import com.fazecast.jSerialComm.SerialPort;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/device-config")
@RequiredArgsConstructor
public class DeviceConfigController {

    private static final Logger log = LoggerFactory.getLogger(DeviceConfigController.class);

    private final ModbusProperties modbusProperties;
    private final ModbusMasterService modbusMaster;
    private final AppProperties appProperties;

    @GetMapping
    public String page(Model model) {
        List<String> ports = listSerialPorts();
        model.addAttribute("availablePorts", ports);
        model.addAttribute("currentPort", modbusProperties.getPortName());
        model.addAttribute("connected", modbusMaster.isConnected());
        model.addAttribute("lastError", modbusMaster.getLastError());
        return "admin/device-config";
    }

    @PostMapping
    public String save(
            @RequestParam String portName,
            RedirectAttributes redirectAttributes) {
        String trimmed = portName != null ? portName.trim() : "";
        if (trimmed.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select or enter a serial port.");
            return "redirect:/admin/device-config";
        }
        modbusMaster.reconnect(trimmed);
        persistPort(trimmed);
        if (modbusMaster.isConnected()) {
            redirectAttributes.addFlashAttribute("success", "Connected on " + trimmed);
        } else {
            String err = modbusMaster.getLastError();
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Saved port " + trimmed + " but connection failed"
                            + (err != null && !err.isBlank() ? ": " + err : "."));
        }
        return "redirect:/admin/device-config";
    }

    private List<String> listSerialPorts() {
        List<String> names = new ArrayList<>();
        try {
            for (SerialPort sp : SerialPort.getCommPorts()) {
                names.add(sp.getSystemPortName());
            }
        } catch (Exception e) {
            log.warn("Could not list serial ports: {}", e.getMessage());
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private void persistPort(String portName) {
        try {
            Path dir = Path.of(appProperties.getDataDir());
            Files.createDirectories(dir);
            Path file = dir.resolve(DeviceConfigLoader.PORT_FILE);
            Properties p = new Properties();
            p.setProperty("system.modbus.port-name", portName);
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "Factory Monitor — saved serial port (do not edit unless the app is stopped)");
            }
        } catch (Exception e) {
            log.warn("Could not persist serial port: {}", e.getMessage());
        }
    }
}
