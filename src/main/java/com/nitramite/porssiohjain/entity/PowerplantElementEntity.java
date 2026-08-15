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

import com.nitramite.porssiohjain.entity.enums.PowerplantElementType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "powerplant_element")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PowerplantElementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", nullable = false, length = 32)
    private PowerplantElementType elementType;

    @Column(name = "icon_name", nullable = false, length = 64)
    private String iconName;

    @Column(name = "display_value", precision = 10, scale = 2)
    private BigDecimal displayValue;

    @Column(name = "display_unit", length = 32)
    private String displayUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private DeviceEntity device;

    @Column(name = "device_channel")
    private Integer deviceChannel;

    @Column(name = "canvas_x", nullable = false)
    private Integer canvasX;

    @Column(name = "canvas_y", nullable = false)
    private Integer canvasY;

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
