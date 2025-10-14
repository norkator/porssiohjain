package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.ControlMode;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateControlRequest {
    private Long accountId;
    private String name;
    private String timezone;
    private BigDecimal maxPriceSnt;
    private Integer dailyOnMinutes;
    private BigDecimal taxPercent;
    private ControlMode mode;
    private Boolean manualOn;
}