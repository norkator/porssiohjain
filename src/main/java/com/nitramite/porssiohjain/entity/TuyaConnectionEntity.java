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

import com.nitramite.porssiohjain.utils.CryptoConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tuya_connection")
@Getter
@Setter
@NoArgsConstructor
public class TuyaConnectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private AccountEntity account;

    @Column(name = "provider_user_id", length = 128)
    private String providerUserId;

    @Column(name = "account_label", length = 255)
    private String accountLabel;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "session_data", columnDefinition = "TEXT")
    private String sessionData;
}
