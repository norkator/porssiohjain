package com.nitramite.porssiohjain.services.models;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Getter
public class PricePredictionResponse {
    private Instant timestamp;
    private BigDecimal priceCents;
}