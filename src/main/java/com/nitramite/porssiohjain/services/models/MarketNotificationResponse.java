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

import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.MarketNotificationMetric;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Data
@Builder
public class MarketNotificationResponse {
    private Long id;
    private String name;
    private String description;
    private MarketNotificationMetric metric;
    private ComparisonType comparisonType;
    private BigDecimal thresholdPrice;
    private LocalTime activeFrom;
    private LocalTime activeTo;
    private String timezone;
    private boolean enabled;
    private Instant lastSentAt;
    private Instant createdAt;
    private Instant updatedAt;
}
