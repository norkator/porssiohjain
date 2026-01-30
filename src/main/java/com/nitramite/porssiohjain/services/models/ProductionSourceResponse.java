package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.ProductionApiType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductionSourceResponse {
    private Long id;
    private UUID uuid;
    private String name;
    private ProductionApiType apiType;
    private BigDecimal currentKw;
    private BigDecimal peakKw;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
