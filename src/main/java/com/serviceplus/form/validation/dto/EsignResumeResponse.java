package com.serviceplus.form.validation.dto;

import java.io.Serializable;

public class EsignResumeResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private String nextActivity;

    public EsignResumeResponse() {
    }

    public EsignResumeResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public EsignResumeResponse(boolean success, String message, String nextActivity) {
        this.success = success;
        this.message = message;
        this.nextActivity = nextActivity;
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

    public String getNextActivity() {
        return nextActivity;
    }

    public void setNextActivity(String nextActivity) {
        this.nextActivity = nextActivity;
    }
}
