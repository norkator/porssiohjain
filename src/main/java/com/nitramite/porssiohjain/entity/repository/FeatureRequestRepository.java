/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.FeatureRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface FeatureRequestRepository extends JpaRepository<FeatureRequestEntity, Long> {
    Page<FeatureRequestEntity> findByAccountId(Long accountId, Pageable pageable);
    Page<FeatureRequestEntity> findByHandled(boolean handled, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FeatureRequestEntity r where r.id = :id")
    Optional<FeatureRequestEntity> findLockedById(Long id);
}
