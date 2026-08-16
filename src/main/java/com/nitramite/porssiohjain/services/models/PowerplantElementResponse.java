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

import com.nitramite.porssiohjain.entity.enums.PowerplantElementType;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
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
public class PowerplantElementResponse {
    private Long id;
    private String name;
    private PowerplantElementType elementType;
    private String iconName;
    private BigDecimal displayValue;
    private String displayUnit;
    private DeviceResponse device;
    private Integer deviceChannel;
    private ZigbeeMeasurementType measurementType;
    private String measurementKey;
    private BigDecimal latestMeasurementValue;
    private Instant latestMeasuredAt;
    private Instant latestReceivedAt;
    private boolean latestMeasurementFresh;
    private Integer canvasX;
    private Integer canvasY;
    private Instant createdAt;
    private Instant updatedAt;
}
