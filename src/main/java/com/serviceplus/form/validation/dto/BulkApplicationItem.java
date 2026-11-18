package com.serviceplus.form.validation.dto;

import java.io.Serializable;

public class BulkApplicationItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String applicationId;
    private String txnId;

    public BulkApplicationItem() {
    }

    public BulkApplicationItem(String applicationId, String txnId) {
        this.applicationId = applicationId;
        this.txnId = txnId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }
}
