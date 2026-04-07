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

import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.utils.CryptoConverter;
import jakarta.persistence.*;
import lombok.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Entity
@Table(name = "device")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "device_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DeviceType deviceType = DeviceType.STANDARD;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false)
    private String deviceName;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "last_communication")
    private Instant lastCommunication;

    @Column(name = "api_online", nullable = false)
    private boolean apiOnline;

    @Column(name = "last_telemetry", columnDefinition = "TEXT")
    private String lastTelemetry;

    @Column(name = "mqtt_online", nullable = false)
    private boolean mqttOnline;

    @Column(name = "mqtt_username", unique = true)
    private String mqttUsername;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "mqtt_password")
    private String mqttPassword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @PrePersist
    public void onCreate() {
        uuid = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = createdAt;

        if (deviceType == null) {
            deviceType = DeviceType.STANDARD;
        }

        if (mqttUsername == null) {
            mqttUsername = "device-" + uuid.toString().substring(0, 8);
        }
        if (mqttPassword == null) {
            byte[] randomBytes = new SecureRandom().generateSeed(12);
            mqttPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
