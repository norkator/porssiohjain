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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "heating_planner_room", uniqueConstraints = {
        @UniqueConstraint(name = "uk_heating_planner_room_settings_name", columnNames = {"settings_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeatingPlannerRoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private HeatingPlannerSettingsEntity settings;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(nullable = false, length = 128)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToOne
    @JoinColumn(name = "room_sensor_device_id")
    private DeviceEntity roomSensorDevice;

    @Column(name = "room_sensor_measurement_key", length = 128)
    private String roomSensorMeasurementKey;

    @ManyToOne
    @JoinColumn(name = "floor_sensor_device_id")
    private DeviceEntity floorSensorDevice;

    @Column(name = "floor_sensor_measurement_key", length = 128)
    private String floorSensorMeasurementKey;

    @Builder.Default
    @Column(name = "normal_floor_temperature", nullable = false, precision = 10, scale = 2)
    private BigDecimal normalFloorTemperature = new BigDecimal("23.00");

    @Builder.Default
    @Column(name = "maximum_preheat_floor_temperature", nullable = false, precision = 10, scale = 2)
    private BigDecimal maximumPreheatFloorTemperature = new BigDecimal("27.00");

    @Builder.Default
    @Column(name = "absolute_maximum_floor_temperature", nullable = false, precision = 10, scale = 2)
    private BigDecimal absoluteMaximumFloorTemperature = new BigDecimal("29.00");

    @Builder.Default
    @Column(name = "discharge_floor_setpoint", nullable = false, precision = 10, scale = 2)
    private BigDecimal dischargeFloorSetpoint = new BigDecimal("19.00");

    @Builder.Default
    @Column(name = "minimum_room_temperature", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumRoomTemperature = new BigDecimal("20.00");

    @Builder.Default
    @Column(name = "target_room_temperature", nullable = false, precision = 10, scale = 2)
    private BigDecimal targetRoomTemperature = new BigDecimal("21.00");

    @Builder.Default
    @Column(name = "maximum_room_temperature", nullable = false, precision = 10, scale = 2)
    private BigDecimal maximumRoomTemperature = new BigDecimal("23.50");

    @Column(name = "heater_power_kw", precision = 10, scale = 3)
    private BigDecimal heaterPowerKw;

    @Column(name = "floor_heating_rate", precision = 10, scale = 4)
    private BigDecimal floorHeatingRate;

    @Column(name = "floor_to_room_rate", precision = 10, scale = 4)
    private BigDecimal floorToRoomRate;

    @Column(name = "room_outdoor_loss_rate", precision = 10, scale = 4)
    private BigDecimal roomOutdoorLossRate;

    @Column(name = "wind_loss_rate", precision = 10, scale = 4)
    private BigDecimal windLossRate;

    @Builder.Default
    @Column(name = "model_parameters_learned", nullable = false)
    private boolean modelParametersLearned = false;

    @Builder.Default
    @Column(name = "model_sample_count", nullable = false)
    private Integer modelSampleCount = 0;

    @Builder.Default
    @Column(name = "model_confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal modelConfidence = BigDecimal.ZERO;

    @Column(name = "model_trained_at")
    private Instant modelTrainedAt;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Builder.Default
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HeatingPlannerRoomHeatSourceEntity> heatSources = new ArrayList<>();

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
        if (sortOrder == null) sortOrder = 0;
        if (normalFloorTemperature == null) normalFloorTemperature = new BigDecimal("23.00");
        if (maximumPreheatFloorTemperature == null) maximumPreheatFloorTemperature = new BigDecimal("27.00");
        if (absoluteMaximumFloorTemperature == null) absoluteMaximumFloorTemperature = new BigDecimal("29.00");
        if (dischargeFloorSetpoint == null) dischargeFloorSetpoint = new BigDecimal("19.00");
        if (minimumRoomTemperature == null) minimumRoomTemperature = new BigDecimal("20.00");
        if (targetRoomTemperature == null) targetRoomTemperature = new BigDecimal("21.00");
        if (maximumRoomTemperature == null) maximumRoomTemperature = new BigDecimal("23.50");
        if (modelSampleCount == null) modelSampleCount = 0;
        if (modelConfidence == null) modelConfidence = BigDecimal.ZERO;
    }
}
