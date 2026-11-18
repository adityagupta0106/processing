package com.serviceplus.form.validation.pattern.enums;

public enum SeriesType {
    NUMERIC,
    ALPHABETIC,
    ALPHANUMERIC;

    public static SeriesType fromString(String value) {
        if (value == null || value.isBlank()) {
            return NUMERIC;
        }
        for (SeriesType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return NUMERIC;
    }
}
