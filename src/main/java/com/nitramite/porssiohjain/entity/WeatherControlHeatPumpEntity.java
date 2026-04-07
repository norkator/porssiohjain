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

package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.WeatherMetricType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "weather_control_heat_pump")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherControlHeatPumpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weather_control_id", nullable = false)
    private WeatherControlEntity weatherControl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "state_hex", nullable = false, columnDefinition = "TEXT")
    private String stateHex;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather_metric", nullable = false, length = 32)
    private WeatherMetricType weatherMetric;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_type", nullable = false, length = 32)
    private ComparisonType comparisonType;

    @Column(name = "threshold_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal thresholdValue;

}
