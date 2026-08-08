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

import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "heating_planner_room_heat_source")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeatingPlannerRoomHeatSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private HeatingPlannerRoomEntity room;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private HeatingPlannerHeatSourceType sourceType;

    @Builder.Default
    @Column(name = "useful_in_calculation", nullable = false)
    private boolean usefulInCalculation = true;

    @ManyToOne
    @JoinColumn(name = "controlling_device_id")
    private DeviceEntity controllingDevice;

    @Column(name = "thermostat_channel")
    private Integer thermostatChannel;

    @Column(name = "estimated_power_kw", precision = 10, scale = 3)
    private BigDecimal estimatedPowerKw;

    @Column(name = "heat_share", precision = 10, scale = 4)
    private BigDecimal heatShare;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        normalizeDefaults();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
        normalizeDefaults();
    }

    private void normalizeDefaults() {
        if (sourceType == null) sourceType = HeatingPlannerHeatSourceType.OTHER;
        if (sortOrder == null) sortOrder = 0;
    }
}
