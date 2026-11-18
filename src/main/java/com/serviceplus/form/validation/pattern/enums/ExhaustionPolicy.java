package com.serviceplus.form.validation.pattern.enums;

public enum ExhaustionPolicy {
    THROW_ERROR,
    BLOCK;

    public static ExhaustionPolicy fromString(String value) {
        if (value == null || value.isBlank()) {
            return THROW_ERROR;
        }
        for (ExhaustionPolicy policy : values()) {
            if (policy.name().equalsIgnoreCase(value.trim())) {
                return policy;
            }
        }
        return THROW_ERROR;
    }
}
