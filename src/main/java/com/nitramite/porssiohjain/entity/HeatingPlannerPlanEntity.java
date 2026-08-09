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

import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "heating_planner_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeatingPlannerPlanEntity {

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

    @Builder.Default
    @Column(name = "plan_version", nullable = false, unique = true)
    private UUID planVersion = UUID.randomUUID();

    @Column(name = "horizon_start", nullable = false)
    private Instant horizonStart;

    @Column(name = "horizon_end", nullable = false)
    private Instant horizonEnd;

    @Column(name = "trigger_reason", nullable = false, length = 128)
    private String triggerReason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HeatingPlannerPlanStatus status = HeatingPlannerPlanStatus.SIMULATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Builder.Default
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HeatingPlannerPlanPointEntity> points = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        if (planVersion == null) planVersion = UUID.randomUUID();
        if (status == null) status = HeatingPlannerPlanStatus.SIMULATED;
    }
}
