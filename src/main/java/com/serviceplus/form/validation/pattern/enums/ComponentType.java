package com.serviceplus.form.validation.pattern.enums;

public enum ComponentType {
    VARIABLE,
    SEPARATOR,
    STATIC;

    public static ComponentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return VARIABLE;
        }
        for (ComponentType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return VARIABLE;
    }
}
