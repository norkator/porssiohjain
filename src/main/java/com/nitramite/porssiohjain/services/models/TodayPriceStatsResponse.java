package com.nitramite.porssiohjain.services.models;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TodayPriceStatsResponse {
    private BigDecimal min;
    private BigDecimal avg;
    private BigDecimal max;
}