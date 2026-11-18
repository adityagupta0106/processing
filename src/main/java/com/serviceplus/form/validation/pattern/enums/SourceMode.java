package com.serviceplus.form.validation.pattern.enums;

public enum SourceMode {
    STATIC,
    DYNAMIC,
    SYSTEM_VARIABLE,
    NONE;

    public static SourceMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return STATIC;
        }
        for (SourceMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return STATIC;
    }
}
