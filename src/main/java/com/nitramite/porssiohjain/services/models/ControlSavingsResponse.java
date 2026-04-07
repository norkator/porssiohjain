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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlSavingsResponse {
    private Long controlId;
    private String controlName;
    private BigDecimal estimatedPowerKw;
    private BigDecimal estimatedUsageKwh;
    private BigDecimal baselineCostEur;
    private BigDecimal controlledCostEur;
    private BigDecimal estimatedSavingsEur;
    private int scheduleEntryCount;
    private int estimatedLoadCount;
}
