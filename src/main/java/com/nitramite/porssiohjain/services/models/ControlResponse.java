package com.nitramite.porssiohjain.services.models;

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
    BigDecimal maxPriceSnt;
    Integer dailyOnMinutes;
    BigDecimal taxPercent;
    Instant createdAt;
    Instant updatedAt;
}