package com.serviceplus.form.validation.dto;

public class DigiLockerIdentityResponseDTO {

    private boolean linked;
    private String digilockerId;
    private String referenceKey;
    private Long userId;
    private String applicationId;

    public boolean isLinked() {
        return linked;
    }

    public void setLinked(boolean linked) {
        this.linked = linked;
    }

    public String getDigilockerId() {
        return digilockerId;
    }

    public void setDigilockerId(String digilockerId) {
        this.digilockerId = digilockerId;
    }

    public String getReferenceKey() {
        return referenceKey;
    }

    public void setReferenceKey(String referenceKey) {
        this.referenceKey = referenceKey;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }
}
