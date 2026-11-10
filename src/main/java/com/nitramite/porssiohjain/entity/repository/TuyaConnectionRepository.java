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

import com.nitramite.porssiohjain.entity.TuyaConnectionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface TuyaConnectionRepository extends JpaRepository<TuyaConnectionEntity, Long> {
    List<TuyaConnectionEntity> findByAccountIdOrderByIdAsc(Long accountId);
    Optional<TuyaConnectionEntity> findByAccountIdAndProviderUserId(Long accountId, String providerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TuyaConnectionEntity c where c.account.id = :accountId and c.id = :connectionId")
    Optional<TuyaConnectionEntity> lockByIdAndAccountId(@Param("connectionId") Long connectionId, @Param("accountId") Long accountId);
}
