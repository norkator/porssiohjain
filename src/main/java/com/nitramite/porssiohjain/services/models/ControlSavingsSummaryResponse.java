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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlSavingsSummaryResponse {
    private Instant from;
    private Instant to;
    private String timezone;
    private String baselineMethod;
    private BigDecimal estimatedPowerKw;
    private BigDecimal estimatedUsageKwh;
    private BigDecimal baselineCostEur;
    private BigDecimal controlledCostEur;
    private BigDecimal estimatedSavingsEur;
    private int controlCount;
    private int controlsWithEstimatedPowerCount;
    private int scheduleEntryCount;
    private List<ControlSavingsResponse> controls;
}
