package com.serviceplus.form.validation.enums;

public enum DigiLockerOperation {

    EKYC("eKYC"),

    PUSH_DOCUMENT("PushDocumentToDigiLocker"),

    GET_ISSUED_DOCUMENTS("GetIssuedDocuments"),

    GET_DOCUMENT("GetDocument"),

    GET_XML("GetXML"),

    GET_EAADHAAR_XML("GetEAadhaarXML");

    private final String activityName;

    DigiLockerOperation(String activityName) {
        this.activityName = activityName;
    }

    public String getActivityName() {
        return activityName;
    }

    public static DigiLockerOperation fromActivity(String activity) {

        if (activity == null || activity.isBlank()) {
            return null;
        }

        String trimmed = activity.trim();
        for (DigiLockerOperation operation : values()) {
            if (operation.name().equalsIgnoreCase(trimmed) || operation.activityName.equalsIgnoreCase(trimmed)) {
                return operation;
            }
        }

        String normalized = trimmed.replace("_", "").toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("push") || normalized.equals("pd")) {
            return PUSH_DOCUMENT;
        }
        if (normalized.contains("kyc") || normalized.contains("auth")) {
            return EKYC;
        }

        return null;
    }
}
