package com.serviceplus.form.validation.pattern.enums;

public enum SeparatorType {
    HYPHEN("-"),
    SLASH("/"),
    UNDERSCORE("_"),
    BACKSLASH("\\");

    private final String symbol;

    SeparatorType(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public static boolean isValidSeparator(String val) {
        return isSupported(val);
    }

    public static boolean isSupported(String val) {
        if (val == null || val.isBlank()) {
            return false;
        }
        String trimmed = val.trim();
        for (SeparatorType sep : values()) {
            if (sep.symbol.equals(trimmed) || sep.name().equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    public static String resolveSymbol(String val) {
        if (val == null || val.isBlank()) {
            return "-";
        }
        String trimmed = val.trim();
        for (SeparatorType sep : values()) {
            if (sep.symbol.equals(trimmed) || sep.name().equalsIgnoreCase(trimmed)) {
                return sep.symbol;
            }
        }
        return trimmed;
    }
}
