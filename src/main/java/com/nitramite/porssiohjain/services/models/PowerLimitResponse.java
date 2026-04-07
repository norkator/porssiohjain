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
    private BigDecimal peakKw;
    private boolean enabled;
    private boolean notifyEnabled;
    private String timezone;
    private Integer limitIntervalMinutes;
    private Long siteId;
    private Long energyContractId;
    private String energyContractName;
    private Long transferContractId;
    private String transferContractName;
    private Instant createdAt;
    private Instant updatedAt;
    private BigDecimal lastTotalKwh; // for viewing only
}
