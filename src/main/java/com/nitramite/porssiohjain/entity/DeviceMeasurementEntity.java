/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Normalized readings shared by planners and integrations. Null measuredAt means a polled cloud snapshot. */
@Entity
@Table(name = "device_measurement", indexes = {
        @Index(name = "idx_device_measurement_device_key_time", columnList = "device_id, measurement_type, measurement_key, observed_at DESC, id DESC"),
        @Index(name = "idx_device_measurement_account_time", columnList = "account_id, observed_at DESC"),
        @Index(name = "idx_device_measurement_received_time", columnList = "received_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMeasurementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DevicePlatform source;

    @Column(name = "source_key", nullable = false, length = 128)
    private String sourceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_type", nullable = false, length = 32)
    private DeviceMeasurementType measurementType;

    @Column(name = "measurement_key", nullable = false, length = 64)
    private String measurementKey;

    @Column(name = "\"value\"", nullable = false, precision = 10, scale = 3)
    private BigDecimal value;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(name = "measured_at")
    private Instant measuredAt;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
