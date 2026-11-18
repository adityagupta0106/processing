package com.serviceplus.form.validation.utility;

import java.security.SecureRandom;

public class TinyUrlUtil {

    private static final String CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "abcdefghijklmnopqrstuvwxyz" +
            "0123456789-";

    private static final int TINY_URL_LENGTH = 7;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TinyUrlUtil() {
    }

    public static String generateTinyCode() {
        StringBuilder tinyCode = new StringBuilder(TINY_URL_LENGTH);

        for (int i = 0; i < TINY_URL_LENGTH; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            tinyCode.append(CHARACTERS.charAt(index));
        }

        return tinyCode.toString();
    }

    public static String generateDocumentTinyUrl(String baseUrl, String serviceUrlHeader) {
        return buildTinyUrl(baseUrl, serviceUrlHeader, "/dt/");
    }

    public static String generateActionTinyUrl(String baseUrl, String serviceUrlHeader) {
        return buildTinyUrl(baseUrl, serviceUrlHeader, "/at/");
    }

    private static String buildTinyUrl(String baseUrl, String serviceUrlHeader, String urlType) {

        StringBuilder url = new StringBuilder(baseUrl);

        if (serviceUrlHeader != null && !serviceUrlHeader.trim().isEmpty()) {
            url.append("/").append(serviceUrlHeader.trim());
        }

        url.append(urlType).append(generateTinyCode());

        return url.toString();
    }
}
