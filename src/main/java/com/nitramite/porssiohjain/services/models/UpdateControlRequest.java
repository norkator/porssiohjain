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

import com.nitramite.porssiohjain.entity.enums.ControlMode;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateControlRequest {
    private String name;
    private BigDecimal maxPriceSnt;
    private BigDecimal minPriceSnt;
    private Integer dailyOnMinutes;
    private BigDecimal taxPercent;
    private ControlMode mode;
    private Boolean manualOn;
    private Boolean alwaysOnBelowMinPrice;
}