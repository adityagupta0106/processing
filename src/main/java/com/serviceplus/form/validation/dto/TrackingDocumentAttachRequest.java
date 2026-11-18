package com.serviceplus.form.validation.dto;

import java.util.List;

public class TrackingDocumentAttachRequest {

    private String applicationId;
    private String applicationRefNo;
    private String processId;
    private String tenantId;
    private String uploadId;
    private String documentName;
    private String sourceType;
    private List<String> viewPermission;
    private String documentTinyUrl;

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationRefNo() {
        return applicationRefNo;
    }

    public void setApplicationRefNo(String applicationRefNo) {
        this.applicationRefNo = applicationRefNo;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public List<String> getViewPermission() {
        return viewPermission;
    }

    public void setViewPermission(List<String> viewPermission) {
        this.viewPermission = viewPermission;
    }

    public String getDocumentTinyUrl() {
        return documentTinyUrl;
    }

    public void setDocumentTinyUrl(String documentTinyUrl) {
        this.documentTinyUrl = documentTinyUrl;
    }
}
