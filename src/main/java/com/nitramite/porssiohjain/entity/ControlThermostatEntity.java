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

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "control_thermostat",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"control_id", "device_id", "thermostat_channel"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ControlThermostatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "control_id", nullable = false)
    private ControlEntity control;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "thermostat_channel", nullable = false)
    private Integer thermostatChannel;

    @Column(name = "curve_json", nullable = false, columnDefinition = "TEXT")
    private String curveJson;

    @Column(name = "min_temperature", precision = 10, scale = 2)
    private BigDecimal minTemperature;

    @Column(name = "max_temperature", precision = 10, scale = 2)
    private BigDecimal maxTemperature;

    @Column(name = "fallback_temperature", precision = 10, scale = 2)
    private BigDecimal fallbackTemperature;

    @Column(name = "estimated_power_kw", precision = 10, scale = 3)
    private BigDecimal estimatedPowerKw;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "last_applied_temperature", precision = 10, scale = 2)
    private BigDecimal lastAppliedTemperature;

    @Column(name = "last_applied_at")
    private Instant lastAppliedAt;
}
