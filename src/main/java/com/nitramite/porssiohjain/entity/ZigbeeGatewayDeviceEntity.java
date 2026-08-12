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
import java.util.UUID;

@Entity
@Table(name = "zigbee_gateway_device")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZigbeeGatewayDeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "gateway_id", nullable = false)
    private UUID gatewayId;

    @Column(name = "zigbee_ieee", nullable = false, length = 16)
    private String zigbeeIeee;

    @Column(nullable = false, length = 64)
    private String profile;

    @Column(name = "custom_name", length = 64)
    private String customName;

    @Column(name = "desired_temperature", precision = 10, scale = 2)
    private BigDecimal desiredTemperature;

    @Column(name = "desired_mode", length = 8)
    private String desiredMode;

    @Column(name = "desired_version", nullable = false)
    @Builder.Default
    private long desiredVersion = 0;

    @Column(name = "desired_at")
    private Instant desiredAt;

    @Column(name = "desired_expires_at")
    private Instant desiredExpiresAt;

    @Column(name = "desired_source", length = 32)
    private String desiredSource;

    @Column(name = "reported_temperature", precision = 10, scale = 2)
    private BigDecimal reportedTemperature;

    @Column(name = "reported_setpoint", precision = 10, scale = 2)
    private BigDecimal reportedSetpoint;

    @Column(name = "reported_mode", length = 8)
    private String reportedMode;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "last_error", length = 512)
    private String lastError;

}
