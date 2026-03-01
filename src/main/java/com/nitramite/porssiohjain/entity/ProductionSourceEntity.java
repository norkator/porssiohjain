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

import com.nitramite.porssiohjain.utils.CryptoConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "production_source")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionSourceEntity {

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

    @Column(name = "current_kw", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal currentKw = BigDecimal.ZERO;

    @Column(name = "peak_kw", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal peakKw = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "api_type", nullable = false, length = 32)
    private ProductionApiType apiType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    // api credential related below

    @Column(name = "app_id")
    private String appId;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "app_secret")
    private String appSecret;

    @Column(name = "email")
    private String email;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "password")
    private String password;

    @Column(name = "station_id")
    private String stationId;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "productionSource",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<ProductionHistoryEntity> history;

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