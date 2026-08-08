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

import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanPointStatus;
import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "heating_planner_plan_point", uniqueConstraints = {
        @UniqueConstraint(name = "uk_heating_planner_plan_point_version_room_time",
                columnNames = {"plan_version", "room_id", "planned_time"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeatingPlannerPlanPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private HeatingPlannerPlanEntity plan;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private HeatingPlannerRoomEntity room;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(name = "plan_version", nullable = false)
    private UUID planVersion;

    @Column(name = "planned_time", nullable = false)
    private Instant plannedTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "price_cents_per_kwh", precision = 10, scale = 4)
    private BigDecimal priceCentsPerKwh;

    @Column(name = "outdoor_temperature", precision = 10, scale = 2)
    private BigDecimal outdoorTemperature;

    @Column(name = "wind_speed_ms", precision = 10, scale = 2)
    private BigDecimal windSpeedMs;

    @Column(name = "predicted_floor_temperature", precision = 10, scale = 2)
    private BigDecimal predictedFloorTemperature;

    @Column(name = "predicted_room_temperature", precision = 10, scale = 2)
    private BigDecimal predictedRoomTemperature;

    @Column(name = "planned_floor_setpoint", precision = 10, scale = 2)
    private BigDecimal plannedFloorSetpoint;

    @Column(name = "predicted_wood_heat_rate", precision = 10, scale = 4)
    private BigDecimal predictedWoodHeatRate;

    @Builder.Default
    @Column(nullable = false)
    private boolean heating = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "operating_mode", nullable = false, length = 32)
    private HeatingPlanSimulationService.OperatingMode operatingMode;

    @Column(nullable = false, length = 1024)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HeatingPlannerPlanPointStatus status = HeatingPlannerPlanPointStatus.SIMULATED;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        if (status == null) status = HeatingPlannerPlanPointStatus.SIMULATED;
    }
}
