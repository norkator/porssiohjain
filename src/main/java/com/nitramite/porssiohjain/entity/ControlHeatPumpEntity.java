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

import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "control_heat_pump")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ControlHeatPumpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "control_id", nullable = false)
    private ControlEntity control;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "state_hex", nullable = false, columnDefinition = "TEXT")
    private String stateHex;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_action", nullable = false)
    private ControlAction controlAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_type")
    private ComparisonType comparisonType;

    @Column(name = "price_limit", precision = 19, scale = 4)
    private BigDecimal priceLimit;

}
