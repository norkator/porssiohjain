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

import com.nitramite.porssiohjain.entity.enums.SiteType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "site")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SiteType type;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "weather_place")
    private String weatherPlace;

    @Builder.Default
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Europe/Helsinki";

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id")
    private AccountEntity account;

    @Builder.Default
    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ControlEntity> controls = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PowerLimitEntity> powerLimits = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductionSourceEntity> productionSources = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        if (timezone == null || timezone.isBlank()) {
            timezone = "Europe/Helsinki";
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
        if (timezone == null || timezone.isBlank()) {
            timezone = "Europe/Helsinki";
        }
    }

}
