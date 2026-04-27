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

import com.nitramite.porssiohjain.entity.enums.AccountTier;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(nullable = false, unique = true)
    private String secret;

    @Column(name = "locale", length = 10, nullable = false)
    @Builder.Default
    private String locale = "en";

    @Column(name = "email", nullable = true)
    private String email;

    @Column(name = "notify_power_limit_exceeded", nullable = false)
    @Builder.Default
    private boolean notifyPowerLimitExceeded = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    @Builder.Default
    private AccountTier tier = AccountTier.FREE;

    @Column(name = "device_limit")
    private Integer deviceLimit;

    @Column(name = "weekly_notification_count", nullable = false)
    @Builder.Default
    private int weeklyNotificationCount = 0;

    @Column(name = "weekly_notification_week_start")
    private LocalDate weeklyNotificationWeekStart;

    @Column(name = "agreed_terms", nullable = false)
    @Builder.Default
    private boolean agreedTerms = false;

    @Column(name = "agreed_terms_at")
    private Instant agreedTermsAt;

    @Column(name = "admin", nullable = false)
    @Builder.Default
    private boolean admin = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
