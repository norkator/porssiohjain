package com.nitramite.porssiohjain.services.models;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class FingridWindForecastResponse {
    private Instant startTime;
    private Instant endTime;
    private BigDecimal value;
}