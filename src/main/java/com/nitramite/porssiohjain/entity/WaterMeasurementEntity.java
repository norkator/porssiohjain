/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "water_measurement", uniqueConstraints = @UniqueConstraint(columnNames = {"water_meter_id", "observed_at"}))
@Getter
@Setter
@NoArgsConstructor
public class WaterMeasurementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "water_meter_id", nullable = false)
    private WaterMeterEntity waterMeter;

    @Column(name = "total_cubic_meters", nullable = false, precision = 20, scale = 6)
    private BigDecimal totalCubicMeters;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;
}
