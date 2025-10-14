package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.ControlMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ControlResponse {
    private Long id;
    private String name;
    private String timezone;
    private BigDecimal maxPriceSnt;
    private Integer dailyOnMinutes;
    private BigDecimal taxPercent;
    private ControlMode mode;
    private Boolean manualOn;
    private Instant createdAt;
    private Instant updatedAt;
}