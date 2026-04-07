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
@Table(name = "site_weather", uniqueConstraints = {
        @UniqueConstraint(name = "uk_site_weather_site_forecast_time", columnNames = {"site_id", "forecast_time"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteWeatherEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(name = "forecast_time", nullable = false)
    private Instant forecastTime;

    @Column(name = "temperature", precision = 10, scale = 2)
    private BigDecimal temperature;

    @Column(name = "wind_speed_ms", precision = 10, scale = 2)
    private BigDecimal windSpeedMs;

    @Column(name = "wind_gust", precision = 10, scale = 2)
    private BigDecimal windGust;

    @Column(name = "humidity", precision = 10, scale = 2)
    private BigDecimal humidity;

    @Column(name = "total_cloud_cover", precision = 10, scale = 2)
    private BigDecimal totalCloudCover;

    @Column(name = "precipitation_amount", precision = 10, scale = 2)
    private BigDecimal precipitationAmount;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        if (fetchedAt == null) {
            fetchedAt = createdAt;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

}
