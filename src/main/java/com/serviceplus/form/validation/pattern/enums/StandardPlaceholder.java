package com.serviceplus.form.validation.pattern.enums;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum StandardPlaceholder {
    PREFIX("{PREFIX}"),
    SUFFIX("{SUFFIX}"),
    SERVICE_CODE("{SERVICE_CODE}"),
    SPGD_ENTITY("{SPGD_ENTITY}"),
    SERVICE_ATTRIBUTE("{SERVICE_ATTRIBUTE}"),
    YYYY("{YYYY}"),
    YY("{YY}"),
    FY("{FY}"),
    MM("{MM}"),
    DD("{DD}"),
    HH("{HH}"),
    mm("{mm}"),
    ss("{ss}"),
    HH_MM_SS("{HH:mm:ss}"),
    SEQ("{SEQ}"),
    CHECKSUM("{CHECKSUM}");

    private final String token;

    StandardPlaceholder(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    private static final Set<String> ALL_TOKENS;

    static {
        Set<String> set = new HashSet<>();
        for (StandardPlaceholder placeholder : values()) {
            set.add(placeholder.token);
        }
        ALL_TOKENS = Collections.unmodifiableSet(set);
    }

    public static boolean isSupported(String token) {
        return token != null && ALL_TOKENS.contains(token.trim());
    }

    public static Set<String> getAllTokens() {
        return ALL_TOKENS;
    }
}
