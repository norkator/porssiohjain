/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.ProductionApiType;
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
    private String timezone;
    private Long siteId;
    private Instant createdAt;
    private Instant updatedAt;

    private String appId;
    private String appSecret;
    private String email;
    private String password;
    private String stationId;
    private String siteName;
    private Boolean shared;
}
