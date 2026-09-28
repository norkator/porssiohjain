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
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    @Query("SELECT a FROM AccountEntity a WHERE LOWER(COALESCE(a.email, '')) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR CAST(a.id AS string) LIKE CONCAT('%', :search, '%') "
            + "OR LOWER(CAST(a.uuid AS string)) LIKE LOWER(CONCAT('%', :search, '%'))")
    org.springframework.data.domain.Page<AccountEntity> searchAdminUsers(
            @org.springframework.data.repository.query.Param("search") String search,
            org.springframework.data.domain.Pageable pageable);

    Optional<AccountEntity> findByUuid(UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountEntity> findWithLockById(Long id);

    @Query("SELECT DISTINCT a.marketIndexName FROM AccountEntity a WHERE a.marketIndexName IS NOT NULL")
    List<String> findDistinctMarketIndexNames();

}
