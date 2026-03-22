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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private DeviceEntity device;


    @Column(name = "ac_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AcType acType = AcType.NONE;


    @Column(name = "ac_username", unique = true)
    private String acUsername;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "ac_password")
    private String acPassword;


    // Todo...


    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

}
