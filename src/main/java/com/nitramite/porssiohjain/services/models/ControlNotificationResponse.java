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

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ControlNotificationResponse {

    private Long id;
    private Long controlId;
    private String name;
    private String description;
    private LocalTime activeFrom;
    private LocalTime activeTo;
    private boolean enabled;
    private BigDecimal cheapestHours;
    private Integer sendEarlierMinutes;
    private Instant lastSentAt;
    private Instant nextSendAt;
    private Instant createdAt;
    private Instant updatedAt;
}
