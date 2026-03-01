/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
