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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "heating_planner_settings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_heating_planner_settings_account_site", columnNames = {"account_id", "site_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeatingPlannerSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    @Builder.Default
    @Column(name = "active_control_enabled", nullable = false)
    private boolean activeControlEnabled = false;

    @Builder.Default
    @Column(nullable = false, length = 64)
    private String timezone = "Europe/Helsinki";

    @Builder.Default
    @Column(name = "planner_active_below_temperature", nullable = false, precision = 10, scale = 2)
    private BigDecimal plannerActiveBelowTemperature = new BigDecimal("5.00");

    @Builder.Default
    @Column(name = "wood_recommendation_below_temperature", nullable = false, precision = 10, scale = 2)
    private BigDecimal woodRecommendationBelowTemperature = new BigDecimal("0.00");

    @Builder.Default
    @Column(name = "tax_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercent = new BigDecimal("25.50");

    @ManyToOne
    @JoinColumn(name = "transfer_contract_id")
    private ElectricityContractEntity transferContract;

    @Builder.Default
    @Column(name = "stove_loaded", nullable = false)
    private boolean stoveLoaded = false;

    @Builder.Default
    @Column(name = "stove_available_from")
    private LocalTime stoveAvailableFrom = LocalTime.of(6, 0);

    @Builder.Default
    @Column(name = "stove_available_to")
    private LocalTime stoveAvailableTo = LocalTime.of(22, 0);

    @Builder.Default
    @Column(name = "wood_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal woodAmount = new BigDecimal("8.00");

    @Builder.Default
    @Column(name = "wood_release_delay_minutes", nullable = false)
    private Integer woodReleaseDelayMinutes = 45;

    @Builder.Default
    @Column(name = "wood_release_duration_minutes", nullable = false)
    private Integer woodReleaseDurationMinutes = 360;

    @Builder.Default
    @Column(name = "cheap_price_threshold", nullable = false, precision = 10, scale = 4)
    private BigDecimal cheapPriceThreshold = new BigDecimal("5.0000");

    @Builder.Default
    @Column(name = "expensive_price_threshold", nullable = false, precision = 10, scale = 4)
    private BigDecimal expensivePriceThreshold = new BigDecimal("20.0000");

    @Builder.Default
    @Column(name = "cheap_price_percentile", nullable = false, precision = 5, scale = 4)
    private BigDecimal cheapPricePercentile = new BigDecimal("0.2500");

    @Builder.Default
    @Column(name = "expensive_price_percentile", nullable = false, precision = 5, scale = 4)
    private BigDecimal expensivePricePercentile = new BigDecimal("0.7500");

    @Builder.Default
    @Column(name = "simulation_step_minutes", nullable = false)
    private Integer simulationStepMinutes = 15;

    @Builder.Default
    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion = "deterministic-v1";

    @Column(name = "last_automatic_plan_at")
    private Instant lastAutomaticPlanAt;

    @Column(name = "last_automatic_activation_at")
    private Instant lastAutomaticActivationAt;

    @Column(name = "last_automation_error", length = 1024)
    private String lastAutomationError;

    @Builder.Default
    @OneToMany(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HeatingPlannerRoomEntity> rooms = new ArrayList<>();

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
        if (timezone == null || timezone.isBlank()) timezone = "Europe/Helsinki";
        if (plannerActiveBelowTemperature == null) plannerActiveBelowTemperature = new BigDecimal("5.00");
        if (woodRecommendationBelowTemperature == null) woodRecommendationBelowTemperature = new BigDecimal("0.00");
        if (taxPercent == null) taxPercent = new BigDecimal("25.50");
        if (stoveAvailableFrom == null) stoveAvailableFrom = LocalTime.of(6, 0);
        if (stoveAvailableTo == null) stoveAvailableTo = LocalTime.of(22, 0);
        if (woodAmount == null) woodAmount = new BigDecimal("8.00");
        if (woodReleaseDelayMinutes == null) woodReleaseDelayMinutes = 45;
        if (woodReleaseDurationMinutes == null) woodReleaseDurationMinutes = 360;
        if (cheapPriceThreshold == null) cheapPriceThreshold = new BigDecimal("5.0000");
        if (expensivePriceThreshold == null) expensivePriceThreshold = new BigDecimal("20.0000");
        if (cheapPricePercentile == null) cheapPricePercentile = new BigDecimal("0.2500");
        if (expensivePricePercentile == null) expensivePricePercentile = new BigDecimal("0.7500");
        if (simulationStepMinutes == null) simulationStepMinutes = 15;
        if (modelVersion == null || modelVersion.isBlank()) modelVersion = "deterministic-v1";
    }
}
