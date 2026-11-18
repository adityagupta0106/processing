package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EsignCompleteRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String applicationId;
    private String taskId;
    private Integer serviceId;
    private String eSignResponse;
    private String espTxnID;
    private String txnId;

    public EsignCompleteRequest() {
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
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

    public String getTxnId() {
        return txnId != null ? txnId : espTxnID;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
        if (this.espTxnID == null) {
            this.espTxnID = txnId;
        }
    }
}
