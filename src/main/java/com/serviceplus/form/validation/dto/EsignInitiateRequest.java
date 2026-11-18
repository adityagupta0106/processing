package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EsignInitiateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String mode;
    private String actionType;
    private String gatewayCode;

    private String applicationId;
    private Integer serviceId;
    private String taskId;
    private String documentId;
    private String signerType;
    private String signerName;
    private String authMode; // "1" - OTP, "2" - Biometric, "3" - Iris, "4" - Face
    private String reason;
    private Integer pageNo;
    private String coordinates;
    private String returnUrl;
    private String responseUrl;

    private String eSignResponse;
    private String espTxnID;
    private String rawBody;
    private Map<String, Object> queryParams;
    private Map<String, Object> headers;
    private Map<String, Object> formData;

    public EsignInitiateRequest() {
    }

    public String getMode() {
        return mode != null ? mode : actionType;
    }

    public void setMode(String mode) {
        this.mode = mode;
        if (this.actionType == null) {
            this.actionType = mode;
        }
    }

    public String getActionType() {
        return actionType != null ? actionType : mode;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
        if (this.mode == null) {
            this.mode = actionType;
        }
    }

    public String getGatewayCode() {
        return gatewayCode;
    }

    public void setGatewayCode(String gatewayCode) {
        this.gatewayCode = gatewayCode;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getSignerType() {
        return signerType;
    }

    public void setSignerType(String signerType) {
        this.signerType = signerType;
    }

    public String getSignerName() {
        return signerName;
    }

    public void setSignerName(String signerName) {
        this.signerName = signerName;
    }

    public String getAuthMode() {
        return authMode;
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public String getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    public String getReturnUrl() {
        return returnUrl != null ? returnUrl : responseUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        if (this.responseUrl == null) {
            this.responseUrl = returnUrl;
        }
    }

    public String getResponseUrl() {
        return responseUrl != null ? responseUrl : returnUrl;
    }

    public void setResponseUrl(String responseUrl) {
        this.responseUrl = responseUrl;
        if (this.returnUrl == null) {
            this.returnUrl = responseUrl;
        }
    }

    public String geteSignResponse() {
        return eSignResponse;
    }

    public void seteSignResponse(String eSignResponse) {
        this.eSignResponse = eSignResponse;
    }

    public String getEspTxnID() {
        return espTxnID;
    }

    public void setEspTxnID(String espTxnID) {
        this.espTxnID = espTxnID;
    }

    public String getRawBody() {
        return rawBody;
    }

    public void setRawBody(String rawBody) {
        this.rawBody = rawBody;
    }

    public Map<String, Object> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, Object> queryParams) {
        this.queryParams = queryParams;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public Map<String, Object> getFormData() {
        return formData;
    }

    public void setFormData(Map<String, Object> formData) {
        this.formData = formData;
    }
}
