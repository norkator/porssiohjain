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

import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.utils.CryptoConverter;
import jakarta.persistence.*;
import lombok.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

@Entity
@Table(name = "device_ac_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceAcDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // there could be multiple indoor units so many to one is used here
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "name", nullable = false)
    private String name; // for multi indoor unit setup like upstairs and living room etc

    @Column(name = "ac_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AcType acType = AcType.NONE;

    @Column(name = "ac_username")
    private String acUsername;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "ac_password", columnDefinition = "TEXT")
    private String acPassword;

    @Column(name = "ac_token_expires_at")
    private Instant acTokenExpiresAt;

    @Column(name = "ac_access_token", columnDefinition = "TEXT")
    private String acAccessToken; // jwt token

    @Column(name = "ac_consumer_id")
    private String acConsumerId; // like uuid

    @Column(name = "ac_device_id")
    private String acDeviceId; // like uuid

    @Column(name = "building_id")
    private String buildingId; // like MELCloud BuildingID

    @Column(name = "ac_device_unique_id")
    private String acDeviceUniqueId; // Toshiba AMQP target id

    @Column(name = "sas_token", columnDefinition = "TEXT")
    private String sasToken; // needed for azure iot hub to send device state changes

    @Column(name = "ac_client_device_suffix", length = 64)
    private String acClientDeviceSuffix;

    @Column(name = "last_polled_state_hex", columnDefinition = "TEXT")
    private String lastPolledStateHex; // last polled or sent state to avoid repeating

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;

        if (acClientDeviceSuffix == null || acClientDeviceSuffix.isBlank()) {
            byte[] randomBytes = new SecureRandom().generateSeed(12);
            acClientDeviceSuffix = HexFormat.of().formatHex(randomBytes);
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

}
