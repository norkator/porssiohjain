package com.nitramite.porssiohjain.services.models;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionHistoryResponse {
    private Instant createdAt;
    private BigDecimal kilowatts;
}