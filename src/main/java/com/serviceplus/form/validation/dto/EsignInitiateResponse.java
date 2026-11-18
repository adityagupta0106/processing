package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.serviceplus.payment.adapter.api.RedirectResponse;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EsignInitiateResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String txnId;
    private String paymentId;
    private String gatewayTransactionId;
    private String status;
    private RedirectResponse redirect;
    private String gatewayUrl;
    private String espGatewayUrl;
    private String eSignRequest;
    private String aspTxnID;
    private String httpMethod = "POST";
    private String requestXml;
    private String autoPostHtml;
    private String message;

    public EsignInitiateResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getTxnId() {
        return txnId != null ? txnId : aspTxnID;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
        if (this.aspTxnID == null) {
            this.aspTxnID = txnId;
        }
        if (this.paymentId == null) {
            this.paymentId = txnId;
        }
        if (this.gatewayTransactionId == null) {
            this.gatewayTransactionId = txnId;
        }
    }

    public String getPaymentId() {
        return paymentId != null ? paymentId : getTxnId();
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId != null ? gatewayTransactionId : getTxnId();
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public RedirectResponse getRedirect() {
        return redirect;
    }

    public void setRedirect(RedirectResponse redirect) {
        this.redirect = redirect;
    }

    public String getGatewayUrl() {
        return gatewayUrl != null ? gatewayUrl : espGatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
        if (this.espGatewayUrl == null) {
            this.espGatewayUrl = gatewayUrl;
        }
    }

    public String getEspGatewayUrl() {
        return espGatewayUrl != null ? espGatewayUrl : gatewayUrl;
    }

    public void setEspGatewayUrl(String espGatewayUrl) {
        this.espGatewayUrl = espGatewayUrl;
        if (this.gatewayUrl == null) {
            this.gatewayUrl = espGatewayUrl;
        }
    }

    public String geteSignRequest() {
        return eSignRequest;
    }

    public void seteSignRequest(String eSignRequest) {
        this.eSignRequest = eSignRequest;
    }

    public String getAspTxnID() {
        return aspTxnID != null ? aspTxnID : txnId;
    }

    public void setAspTxnID(String aspTxnID) {
        this.aspTxnID = aspTxnID;
        if (this.txnId == null) {
            this.txnId = aspTxnID;
        }
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getRequestXml() {
        return requestXml;
    }

    public void setRequestXml(String requestXml) {
        this.requestXml = requestXml;
    }

    public String getAutoPostHtml() {
        return autoPostHtml;
    }

    public void setAutoPostHtml(String autoPostHtml) {
        this.autoPostHtml = autoPostHtml;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
