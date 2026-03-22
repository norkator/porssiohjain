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

import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.utils.CryptoConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

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
    private AcType acType = AcType.NONE;

    @Column(name = "ac_username")
    private String acUsername;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "ac_password")
    private String acPassword;

    @Column(name = "ac_access_token")
    private String acAccessToken; // jwt token

    @Column(name = "ac_consumer_id")
    private String acConsumerId; // like uuid

    @Column(name = "ac_device_id")
    private String acDeviceId; // like uuid

    @Column(name = "sas_token")
    private String sasToken; // needed for azure iot hub to send device state changes

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

}
