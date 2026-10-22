/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import com.nitramite.porssiohjain.entity.enums.WaterMeterNotificationFrequency;
import com.nitramite.porssiohjain.utils.CryptoConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "water_meter")
@Getter
@Setter
@NoArgsConstructor
public class WaterMeterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WaterMeterProvider provider = WaterMeterProvider.WRM_SYSTEMS_WMD;

    @Column(name = "portal_url", nullable = false, length = 255)
    private String portalUrl;

    @JsonIgnore
    @Convert(converter = CryptoConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String username;

    @JsonIgnore
    @Convert(converter = CryptoConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "latest_total_cubic_meters", precision = 20, scale = 6)
    private BigDecimal latestTotalCubicMeters;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "last_poll_attempt_at")
    private Instant lastPollAttemptAt;

    @Column(name = "last_error_key", length = 100)
    private String lastErrorKey;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_frequency", nullable = false, length = 16)
    private WaterMeterNotificationFrequency pushFrequency = WaterMeterNotificationFrequency.DAILY;

    @Column(name = "push_time", nullable = false)
    private LocalTime pushTime = LocalTime.of(12, 5);

    @Column(name = "push_weekday", nullable = false)
    private int pushWeekday = 1;

    @Column(name = "push_timezone", nullable = false, length = 64)
    private String pushTimezone = "Europe/Helsinki";

    @Column(name = "last_push_sent_at")
    private Instant lastPushSentAt;
}
