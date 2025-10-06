package com.nitramite.porssiohjain.services.models;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateControlRequest {
    private Long accountId;
    private String name;
    private BigDecimal maxPriceSnt;
    private Integer dailyOnMinutes;
}