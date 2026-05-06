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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlThermostatResponse {
    private Long id;
    private Long controlId;
    private Long deviceId;
    private Integer thermostatChannel;
    private String curveJson;
    private BigDecimal minTemperature;
    private BigDecimal maxTemperature;
    private BigDecimal fallbackTemperature;
    private BigDecimal estimatedPowerKw;
    private boolean enabled;
    private BigDecimal lastAppliedTemperature;
    private Instant lastAppliedAt;
    private DeviceResponse device;
}
