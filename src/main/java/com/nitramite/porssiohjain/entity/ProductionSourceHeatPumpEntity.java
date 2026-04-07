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
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "production_source_heat_pump")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionSourceHeatPumpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_source_id", nullable = false)
    private ProductionSourceEntity productionSource;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "state_hex", nullable = false, columnDefinition = "TEXT")
    private String stateHex;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_action", nullable = false)
    private ControlAction controlAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_type", nullable = false)
    private ComparisonType comparisonType;

    @Column(name = "trigger_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal triggerKw;

}
