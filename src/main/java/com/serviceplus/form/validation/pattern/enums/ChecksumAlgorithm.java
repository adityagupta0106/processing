package com.serviceplus.form.validation.pattern.enums;

public enum ChecksumAlgorithm {
    LUHN,
    VERHOEFF,
    NONE;

    public static ChecksumAlgorithm fromString(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        for (ChecksumAlgorithm alg : values()) {
            if (alg.name().equalsIgnoreCase(value.trim())) {
                return alg;
            }
        }
        return NONE;
    }
}
