package com.serviceplus.form.validation.pattern.enums;

public enum PatternType {
    APPLICATION,
    CERTIFICATE,
    ACKNOWLEDGMENT,
    REGISTRATION,
    RECEIPT,
    TOKEN;

    public static PatternType fromString(String value) {
        if (value == null || value.isBlank()) {
            return APPLICATION;
        }
        for (PatternType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return APPLICATION;
    }
}
