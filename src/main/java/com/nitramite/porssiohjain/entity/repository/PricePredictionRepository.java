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

import com.nitramite.porssiohjain.entity.PricePredictionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface PricePredictionRepository extends JpaRepository<PricePredictionEntity, Long> {

    @Query("""
                select p from PricePredictionEntity p
                where p.timestamp between :start and :end
                order by p.timestamp
            """)
    List<PricePredictionEntity> findBetween(Instant start, Instant end);

    void deleteByTimestampBefore(Instant instant);

    boolean existsByTimestampBetween(Instant start, Instant end);

    List<PricePredictionEntity> findByTimestampAfterOrderByTimestampAsc(Instant timestamp);

}