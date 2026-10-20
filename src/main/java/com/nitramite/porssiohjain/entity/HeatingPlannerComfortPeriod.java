/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalTime;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HeatingPlannerComfortPeriod {
    @Column(name = "time_from", nullable = false)
    private LocalTime from;
    @Column(name = "time_to", nullable = false)
    private LocalTime to;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal target;
}
