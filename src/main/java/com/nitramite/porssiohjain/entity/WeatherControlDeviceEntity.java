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
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.WeatherMetricType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "weather_control_device",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"weather_control_id", "device_id", "device_channel"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherControlDeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weather_control_id", nullable = false)
    private WeatherControlEntity weatherControl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "device_channel", nullable = false)
    private Integer deviceChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather_metric", nullable = false, length = 32)
    private WeatherMetricType weatherMetric;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_type", length = 32)
    private ComparisonType comparisonType;

    @Column(name = "threshold_value", precision = 19, scale = 4)
    private BigDecimal thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_action", nullable = false, length = 32)
    private ControlAction controlAction;

    @Column(name = "priority_rule", nullable = false)
    private boolean priorityRule;

}
