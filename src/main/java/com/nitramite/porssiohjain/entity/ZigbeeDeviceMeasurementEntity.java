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

import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "zigbee_device_measurement", indexes = {
        @Index(name = "idx_zigbee_measurement_device_type_time", columnList = "device_id, measurement_type, measured_at DESC"),
        @Index(name = "idx_zigbee_measurement_account_time", columnList = "account_id, measured_at DESC"),
        @Index(name = "idx_zigbee_measurement_gateway_ieee_time", columnList = "gateway_id, zigbee_ieee, measured_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZigbeeDeviceMeasurementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "gateway_id", nullable = false)
    private UUID gatewayId;

    @Column(name = "zigbee_ieee", nullable = false, length = 16)
    private String zigbeeIeee;

    @Column(nullable = false, length = 64)
    private String profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_type", nullable = false, length = 32)
    private ZigbeeMeasurementType measurementType;

    @Column(name = "measurement_key", nullable = false, length = 64)
    private String measurementKey;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal value;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @PrePersist
    public void onCreate() {
        if (receivedAt == null) receivedAt = Instant.now();
        if (measuredAt == null) measuredAt = receivedAt;
    }
}
