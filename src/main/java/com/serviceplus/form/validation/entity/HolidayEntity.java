package com.serviceplus.form.validation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Table("schm_sp.spe_holidaylist")
public class HolidayEntity {

    @Id
    private Long id;

    @Column("spdi_unit_code")
    private Integer spdiUnitCode;

    @Column("spdv_unit_type")
    private String spdvUnitType;

    @Column("spdv_holidaydate")
    private LocalDate spdvHolidaydate;

    @Column("spdi_cal_year")
    private Integer spdiCalYear;

    @Column("spdv_weekday")
    private String spdvWeekday;

    @Column("spdv_holiday_description")
    private String spdvHolidayDescription;

    public void setSpdiUnitCode(Integer spdiUnitCode) { this.spdiUnitCode = spdiUnitCode; }
    public void setSpdvUnitType(String spdvUnitType) { this.spdvUnitType = spdvUnitType; }
    public void setSpdvHolidaydate(LocalDate spdvHolidaydate) { this.spdvHolidaydate = spdvHolidaydate; }
    public void setSpdiCalYear(Integer spdiCalYear) { this.spdiCalYear = spdiCalYear; }
    public void setSpdvWeekday(String spdvWeekday) { this.spdvWeekday = spdvWeekday; }
    public void setSpdvHolidayDescription(String spdvHolidayDescription) { this.spdvHolidayDescription = spdvHolidayDescription; }
}
