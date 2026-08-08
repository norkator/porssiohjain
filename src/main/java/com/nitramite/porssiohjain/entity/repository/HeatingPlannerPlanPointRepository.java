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

import com.nitramite.porssiohjain.entity.HeatingPlannerPlanPointEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanPointStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HeatingPlannerPlanPointRepository extends JpaRepository<HeatingPlannerPlanPointEntity, Long> {

    List<HeatingPlannerPlanPointEntity> findByRoomIdAndPlannedTimeBetweenOrderByPlannedTimeAsc(
            Long roomId, Instant start, Instant end);

    List<HeatingPlannerPlanPointEntity> findByAccountIdAndSiteIdAndStatusAndPlannedTimeBetweenOrderByPlannedTimeAsc(
            Long accountId, Long siteId, HeatingPlannerPlanPointStatus status, Instant start, Instant end);

    List<HeatingPlannerPlanPointEntity> findByPlanVersion(UUID planVersion);
}
