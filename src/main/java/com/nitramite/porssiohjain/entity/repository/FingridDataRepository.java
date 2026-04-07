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

import com.nitramite.porssiohjain.entity.FingridDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FingridDataRepository extends JpaRepository<FingridDataEntity, Long> {

    List<FingridDataEntity> findByDatasetIdAndStartTimeAfter(Integer datasetId, Instant startTime);

    boolean existsByDatasetIdAndStartTimeBetween(Integer datasetId, Instant start, Instant end);

    void deleteByStartTimeBefore(Instant cutoff);

}