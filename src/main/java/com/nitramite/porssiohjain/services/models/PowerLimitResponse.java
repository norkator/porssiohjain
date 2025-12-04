package com.nitramite.porssiohjain.services.models;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PowerLimitResponse {
    private Long id;
    private UUID uuid;
    private String name;
    private BigDecimal limitKw;
    private BigDecimal currentKw;
    private boolean enabled;
    private String timezone;
    private Instant createdAt;
    private Instant updatedAt;
}
