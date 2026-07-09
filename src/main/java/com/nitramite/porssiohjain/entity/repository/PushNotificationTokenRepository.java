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

package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.PushNotificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PushNotificationTokenRepository extends JpaRepository<PushNotificationTokenEntity, Long> {

    List<PushNotificationTokenEntity> findByAccountIdAndInvalidatedAtIsNullOrderByUpdatedAtDesc(Long accountId);

    Optional<PushNotificationTokenEntity> findByIdAndAccountId(Long id, Long accountId);

    Optional<PushNotificationTokenEntity> findByToken(String token);

    boolean existsByAccountIdAndInvalidatedAtIsNull(Long accountId);

    @Query("""
            SELECT token
            FROM PushNotificationTokenEntity token
            JOIN FETCH token.account account
            WHERE account.admin = true
            AND token.invalidatedAt IS NULL
            ORDER BY token.updatedAt DESC
            """)
    List<PushNotificationTokenEntity> findActiveAdminTokensOrderByUpdatedAtDesc();
}
