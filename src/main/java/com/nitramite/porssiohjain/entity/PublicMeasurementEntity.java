/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "public_measurement", uniqueConstraints = @UniqueConstraint(
        columnNames = {"account_id", "device_id", "measurement_type", "measurement_key"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicMeasurementEntity {
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
    @Column(name = "measurement_type", nullable = false, length = 32)
    private DeviceMeasurementType measurementType;

    @Column(name = "measurement_key", nullable = false, length = 64)
    private String measurementKey;
}
