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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ControlRepository extends JpaRepository<ControlEntity, Long> {

    List<ControlEntity> findAllByAccountOrderByIdAsc(AccountEntity account);

    Optional<ControlEntity> findByIdAndAccountId(Long id, Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ControlEntity c where c.id = :id and c.account.id = :accountId")
    Optional<ControlEntity> lockHeatPumpRuleUpdate(@Param("id") Long id, @Param("accountId") Long accountId);

    Optional<ControlEntity> findFirstByAccountId(Long accountId);

    List<ControlEntity> findByAccountId(Long accountId);

    long countByAccountId(Long accountId);

}
