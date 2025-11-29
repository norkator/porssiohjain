package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.ControlMode;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateControlRequest {
    private String name;
    private BigDecimal maxPriceSnt;
    private BigDecimal minPriceSnt;
    private Integer dailyOnMinutes;
    private BigDecimal taxPercent;
    private ControlMode mode;
    private Boolean manualOn;
    private Boolean alwaysOnBelowMinPrice;
}