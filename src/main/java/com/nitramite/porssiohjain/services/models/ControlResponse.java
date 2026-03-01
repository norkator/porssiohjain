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

import com.nitramite.porssiohjain.entity.ControlMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ControlResponse {
    private Long id;
    private String name;
    private String timezone;
    private BigDecimal maxPriceSnt;
    private BigDecimal minPriceSnt;
    private Integer dailyOnMinutes;
    private BigDecimal taxPercent;
    private ControlMode mode;
    private Boolean manualOn;
    private Boolean alwaysOnBelowMinPrice;
    private Long energyContractId;
    private String energyContractName;
    private Long transferContractId;
    private String transferContractName;
    private Long siteId;
    private Instant createdAt;
    private Instant updatedAt;
}