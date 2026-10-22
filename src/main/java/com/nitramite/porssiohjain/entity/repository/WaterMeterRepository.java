/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.WaterMeterEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaterMeterRepository extends JpaRepository<WaterMeterEntity, Long> {
    List<WaterMeterEntity> findByAccountIdOrderByNameAscIdAsc(Long accountId);

    boolean existsByIdAndAccountId(Long id, Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WaterMeterEntity w join fetch w.account where w.id = :id and w.account.id = :accountId")
    Optional<WaterMeterEntity> lockByIdAndAccountId(@Param("id") Long id, @Param("accountId") Long accountId);

    @Query("select w from WaterMeterEntity w join fetch w.account where w.enabled = true and w.account.demo = false order by w.id")
    List<WaterMeterEntity> findEnabledForPolling();

    @Query("select w from WaterMeterEntity w join fetch w.account where w.enabled = true and w.pushEnabled = true and w.account.demo = false order by w.id")
    List<WaterMeterEntity> findEnabledForNotifications();
}
