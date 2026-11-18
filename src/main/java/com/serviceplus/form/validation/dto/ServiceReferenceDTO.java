package com.serviceplus.form.validation.dto;

public class ServiceReferenceDTO {

    private String serviceAbbreviation;
    private String serviceUrlHeader;

    public String getServiceAbbreviation() {
        return serviceAbbreviation;
    }

    public void setServiceAbbreviation(String serviceAbbreviation) {
        this.serviceAbbreviation = serviceAbbreviation;
    }

    public String getServiceUrlHeader() {
        return serviceUrlHeader;
    }

    public void setServiceUrlHeader(String serviceUrlHeader) {
        this.serviceUrlHeader = serviceUrlHeader;
    }
}
