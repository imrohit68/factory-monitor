package com.example.system.config;

public enum OperationMode {
    PRODUCTION("Production Mode"),
    MAINTENANCE("Maintenance Mode");

    private final String displayName;

    OperationMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static OperationMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return PRODUCTION;
        }
        String normalized = raw.trim().toUpperCase();
        if ("MAINTENANCE".equals(normalized) || "SIMULATION".equals(normalized)) {
            return MAINTENANCE;
        }
        return PRODUCTION;
    }
}
