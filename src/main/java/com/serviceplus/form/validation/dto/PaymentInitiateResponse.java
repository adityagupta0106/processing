package com.serviceplus.form.validation.dto;

import com.serviceplus.payment.adapter.api.PaymentStatus;
import com.serviceplus.payment.adapter.api.RedirectResponse;

import java.math.BigDecimal;

public class PaymentInitiateResponse {

    private String paymentId;

    private String gatewayTransactionId;

    private PaymentStatus status;

    private RedirectResponse redirect;

    private BigDecimal amount;

    private String currency;

    public PaymentInitiateResponse() {
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(
            String gatewayTransactionId) {

        this.gatewayTransactionId =
                gatewayTransactionId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public RedirectResponse getRedirect() {
        return redirect;
    }

    public void setRedirect(RedirectResponse redirect) {
        this.redirect = redirect;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}