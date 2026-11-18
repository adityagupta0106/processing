package com.serviceplus.form.validation.dto;

public class PaymentRevalidationResponse {
    private boolean success;
    private String message;
    private String status;

    public PaymentRevalidationResponse() {
    }

    public PaymentRevalidationResponse(
            boolean success,
            String message,
            String status) {
        this.success = success;
        this.message = message;
        this.status = status;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}