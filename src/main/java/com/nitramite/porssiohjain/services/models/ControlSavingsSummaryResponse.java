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
