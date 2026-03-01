/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "power_limit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PowerLimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(name = "limit_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal limitKw;

    @Column(name = "current_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentKw;

    @Builder.Default
    @Column(name = "peak_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal peakKw = BigDecimal.ZERO;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "notify_enabled", nullable = false)
    @Builder.Default
    private boolean notifyEnabled = false;

    @Column(name = "last_total_kwh", precision = 12, scale = 3)
    private BigDecimal lastTotalKwh;

    @Column(name = "last_measured_at")
    private Instant lastMeasuredAt;

    @Column(name = "limit_interval_minutes", nullable = false)
    @Builder.Default
    private Integer limitIntervalMinutes = 60;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "energy_contract_id")
    private ElectricityContractEntity energyContract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_contract_id")
    private ElectricityContractEntity transferContract;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "powerLimit", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PowerLimitDeviceEntity> powerLimitDevices;

    @OneToMany(
            mappedBy = "powerLimit",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<PowerLimitHistoryEntity> history;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;

        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}