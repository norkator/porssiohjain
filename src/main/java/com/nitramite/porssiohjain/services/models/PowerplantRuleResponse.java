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

import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.PowerplantComparisonType;
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
public class PowerplantRuleResponse {
    private Long id;
    private PowerplantElementResponse sourceElement;
    private PowerplantElementResponse targetElement;
    private PowerplantComparisonType comparisonType;
    private BigDecimal thresholdValue;
    private BigDecimal hysteresisValue;
    private ControlAction targetAction;
    private boolean enabled;
    private Integer cooldownSeconds;
    private Boolean lastConditionMatched;
    private Instant lastCommandSentAt;
    private Instant lastEvaluatedAt;
    private String lastSkipReason;
    private Integer controlPointX;
    private Integer controlPointY;
    private Instant createdAt;
    private Instant updatedAt;
}
