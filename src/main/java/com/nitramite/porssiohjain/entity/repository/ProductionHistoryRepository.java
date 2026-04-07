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

import com.nitramite.porssiohjain.entity.ProductionHistoryEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProductionHistoryRepository extends JpaRepository<ProductionHistoryEntity, Long> {

    List<ProductionHistoryEntity> findAllByProductionSource(ProductionSourceEntity source);

    @Modifying
    @Query("""
                DELETE FROM ProductionHistoryEntity ph
                WHERE ph.createdAt < :cutoff
            """)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

}