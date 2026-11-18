package com.serviceplus.form.validation.dto;

import java.io.Serializable;

public class ApplicationCountResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long count;

    public ApplicationCountResponseDTO() {
    }

    public ApplicationCountResponseDTO(long count) {
        this.count = count;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public long getSubmittedCount() {
        return count;
    }

    public void setSubmittedCount(long submittedCount) {
        this.count = submittedCount;
    }
}
