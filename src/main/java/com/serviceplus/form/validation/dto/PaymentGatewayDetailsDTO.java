package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.List;

public class PaymentGatewayDetailsDTO {

    @JsonIgnore
    private List<PaymentGatewayDTO> gateway;
    private List<PaymentConfigurationDTO.PaymentMode> paymentModes;
    @JsonIgnore
    private String currency;
    @JsonIgnore
    private BigDecimal charge;
    @JsonIgnore
    private Integer location;

    public List<PaymentGatewayDTO> getGateway() {
        return gateway;
    }

    public void setGateway(List<PaymentGatewayDTO> gateway) {
        this.gateway = gateway;
    }

    public List<PaymentConfigurationDTO.PaymentMode> getPaymentModes() {
        return paymentModes;
    }

    public void setPaymentModes(List<PaymentConfigurationDTO.PaymentMode> paymentModes) {
        this.paymentModes = paymentModes;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getCharge() {
        return charge;
    }

    public void setCharge(BigDecimal charge) {
        this.charge = charge;
    }

    public Integer getLocation() {
        return location;
    }

    public void setLocation(Integer location) {
        this.location = location;
    }

    public static class PaymentGatewayDTO {

        private String name;
        private Integer id;

        public PaymentGatewayDTO() {
        }

        public PaymentGatewayDTO(String name, Integer id) {
            this.name = name;
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }
    }
}