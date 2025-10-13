package com.nitramite.porssiohjain.services.models;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class NordpoolPriceResponse {
    private Instant deliveryStart;
    private Instant deliveryEnd;
    private BigDecimal priceFi;
    private BigDecimal priceFiWithTax;
}