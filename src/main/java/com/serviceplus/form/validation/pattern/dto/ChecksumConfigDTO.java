package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChecksumConfigDTO {

    private Boolean enabled = false;

    /**
     * Algorithm: NONE, LUHN, VERHOEFF
     */
    private String algorithm = "NONE";

    public ChecksumConfigDTO() {
    }

    public ChecksumConfigDTO(Boolean enabled, String algorithm) {
        this.enabled = enabled;
        this.algorithm = algorithm;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
