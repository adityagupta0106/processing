package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DigiLockerPushDocumentRequestDTO {

    private String applicationId;

    private Long userId;

    private String tenantId;

    private String applicationReferenceNo;

    private Long applicationCertId;

    private String digilockerId;

    private String uri;

    private String documentUri;

    private String docType;

    private String documentType;

    private String description;

    private String docId;

    private String issueDate;

    private String validFrom;

    private String validTo;

    private String action;

    private String ts;

    private String uploadId;

    private String fileName;

    private String mimeType;

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getApplicationReferenceNo() {
        return applicationReferenceNo;
    }

    public void setApplicationReferenceNo(String applicationReferenceNo) {
        this.applicationReferenceNo = applicationReferenceNo;
    }

    public Long getApplicationCertId() {
        return applicationCertId;
    }

    public void setApplicationCertId(Long applicationCertId) {
        this.applicationCertId = applicationCertId;
    }

    public String getDigilockerId() {
        return digilockerId;
    }

    public void setDigilockerId(String digilockerId) {
        this.digilockerId = digilockerId;
    }

    public String getUri() {
        return uri != null ? uri : documentUri;
    }

    public void setUri(String uri) {
        this.uri = uri;
        if (this.documentUri == null) {
            this.documentUri = uri;
        }
    }

    public String getDocumentUri() {
        return documentUri != null ? documentUri : uri;
    }

    public void setDocumentUri(String documentUri) {
        this.documentUri = documentUri;
        if (this.uri == null) {
            this.uri = documentUri;
        }
    }

    public String getDocType() {
        return docType != null ? docType : documentType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
        if (this.documentType == null) {
            this.documentType = docType;
        }
    }

    public String getDocumentType() {
        return documentType != null ? documentType : docType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
        if (this.docType == null) {
            this.docType = documentType;
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(String issueDate) {
        this.issueDate = issueDate;
    }

    public String getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }

    public String getValidTo() {
        return validTo;
    }

    public void setValidTo(String validTo) {
        this.validTo = validTo;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}
