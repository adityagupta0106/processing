package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DigiLockerEKYCRequestDTO {

    private String applicationId;

    private Long userId;

    private String tenantId;

    private String attributeId;

    private String documentType;

    private String applicationReferenceNo;

    private String digilockerId;

    private String accessToken;

    private String certXml;

    private Integer serviceId;

    private String authUrl;

    private String redirectUri;

    private String code;

    private String state;

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

    public String getAttributeId() {
        return attributeId;
    }

    public void setAttributeId(String attributeId) {
        this.attributeId = attributeId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getApplicationReferenceNo() {
        return applicationReferenceNo;
    }

    public void setApplicationReferenceNo(String applicationReferenceNo) {
        this.applicationReferenceNo = applicationReferenceNo;
    }

    public String getDigilockerId() {
        return digilockerId;
    }

    public void setDigilockerId(String digilockerId) {
        this.digilockerId = digilockerId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getCertXml() {
        return certXml;
    }

    public void setCertXml(String certXml) {
        this.certXml = certXml;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public void setAuthUrl(String authUrl) {
        this.authUrl = authUrl;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
