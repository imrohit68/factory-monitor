package com.example.system.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApplicationOperationMode {

    private final OperationMode currentMode;

    public ApplicationOperationMode(Environment environment) {
        OperationMode override = SingleInstanceSupport.getOperationModeOverride();
        this.currentMode =
                override != null
                        ? override
                        : OperationMode.from(environment.getProperty("system.operation-mode"));
    }

    public OperationMode getCurrentMode() {
        return currentMode;
    }

    public boolean isMaintenanceMode() {
        return currentMode == OperationMode.MAINTENANCE;
    }
}
