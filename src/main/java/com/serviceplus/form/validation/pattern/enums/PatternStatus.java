package com.serviceplus.form.validation.pattern.enums;

public enum PatternStatus {
    ACTIVE,
    DRAFT,
    INACTIVE,
    RETIRED,
    SUPERSEDED,
    PREVIEW;

    public static PatternStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        for (PatternStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return ACTIVE;
    }
}
