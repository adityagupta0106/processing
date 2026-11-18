package com.serviceplus.form.validation.dto;

import java.math.BigDecimal;
import java.util.List;

public class PaymentOptionsResponse {

    private boolean success;

    private String message;

    private Integer chargeId;

    private BigDecimal amount;

    private List<PaymentGatewayDetailsDTO> paymentOptions;

    public PaymentOptionsResponse() {
    }

    public PaymentOptionsResponse(boolean success, String message, Integer chargeId,BigDecimal amount, List<PaymentGatewayDetailsDTO> paymentOptions) {

        this.success = success;
        this.message = message;
        this.chargeId = chargeId;
        this.amount = amount;
        this.paymentOptions = paymentOptions;
    }



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

    public Integer getChargeId() {
        return chargeId;
    }

    public void setChargeId(Integer chargeId) {
        this.chargeId = chargeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public List<PaymentGatewayDetailsDTO> getPaymentOptions() {
        return paymentOptions;
    }

    public void setPaymentOptions(List<PaymentGatewayDetailsDTO> paymentOptions) {
        this.paymentOptions = paymentOptions;
    }
}