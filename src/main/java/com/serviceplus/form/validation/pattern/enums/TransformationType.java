package com.serviceplus.form.validation.pattern.enums;

public enum TransformationType {
    UPPERCASE,
    LOWERCASE,
    NONE;

    public static TransformationType fromString(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        for (TransformationType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return NONE;
    }
}
