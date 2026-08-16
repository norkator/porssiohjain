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

import java.time.Instant;

@Entity
@Table(name = "powerplant_settings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_powerplant_settings_account", columnNames = {"account_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PowerplantSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Builder.Default
    @Column(name = "board_width", nullable = false)
    private Integer boardWidth = 1600;

    @Builder.Default
    @Column(name = "board_height", nullable = false)
    private Integer boardHeight = 900;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        normalizeDefaults();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
        normalizeDefaults();
    }

    private void normalizeDefaults() {
        if (boardWidth == null) boardWidth = 1600;
        if (boardHeight == null) boardHeight = 900;
    }
}
