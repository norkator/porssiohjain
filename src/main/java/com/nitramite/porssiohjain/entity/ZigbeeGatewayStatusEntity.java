/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "zigbee_gateway_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZigbeeGatewayStatusEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(name = "gateway_id", nullable = false, unique = true)
    private UUID gatewayId;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(name = "offline", nullable = false)
    @Builder.Default
    private boolean offline = false;

    @Column(name = "offline_detected_at")
    private Instant offlineDetectedAt;
}
