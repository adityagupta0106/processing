package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HolidayDocumentDto {

    private String id;
    private String tenantId;
    private Integer year;

    private List<HolidayEntry> holidays = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public List<HolidayEntry> getHolidays() {
        return holidays;
    }

    public void setHolidays(List<HolidayEntry> holidays) {
        this.holidays = holidays;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HolidayEntry {

        private LocalDate date;
        private String weekday;
        private String description;

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public String getWeekday() {
            return weekday;
        }

        public void setWeekday(String weekday) {
            this.weekday = weekday;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}