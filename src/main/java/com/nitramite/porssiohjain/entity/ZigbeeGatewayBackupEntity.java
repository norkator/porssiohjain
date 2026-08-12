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
import java.util.UUID;

@Entity
@Table(name = "zigbee_gateway_backup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZigbeeGatewayBackupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(name = "gateway_id", nullable = false, unique = true)
    private UUID gatewayId;

    @Column(name = "coordinator_ieee", nullable = false, length = 16)
    private String coordinatorIeee;

    @Column(name = "pan_id", nullable = false)
    private int panId;

    @Column(name = "extended_pan_id", nullable = false, length = 16)
    private String extendedPanId;

    @Column(nullable = false)
    private int channel;

    @Column(name = "devices_json", nullable = false, columnDefinition = "TEXT")
    private String devicesJson;

    @Column(name = "backup_version", nullable = false)
    private int backupVersion;

    @Column(nullable = false)
    private long revision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
