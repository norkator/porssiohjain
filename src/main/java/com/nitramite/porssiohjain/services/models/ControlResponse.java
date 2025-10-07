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
    BigDecimal maxPriceSnt;
    Integer dailyOnMinutes;
    Instant createdAt;
    Instant updatedAt;
}