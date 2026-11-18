package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DigiLockerResponseDTO {

    private boolean success;

    private String message;

    private String applicationId;

    private String digilockerId;

    private String referenceKey;

    private String documentUri;

    private String uri;

    private String uploadId;

    private String documentType;

    private String docId;

    private String authUrl;

    private boolean linked;

    private Map<String, String> extractedAttributes;

    private String rawResponse;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
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

    public String getDocumentUri() {
        return documentUri != null ? documentUri : uri;
    }

    public void setDocumentUri(String documentUri) {
        this.documentUri = documentUri;
        if (this.uri == null) {
            this.uri = documentUri;
        }
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

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public void setAuthUrl(String authUrl) {
        this.authUrl = authUrl;
    }

    public boolean isLinked() {
        return linked;
    }

    public void setLinked(boolean linked) {
        this.linked = linked;
    }

    public Map<String, String> getExtractedAttributes() {
        return extractedAttributes;
    }

    public void setExtractedAttributes(Map<String, String> extractedAttributes) {
        this.extractedAttributes = extractedAttributes;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }
}
