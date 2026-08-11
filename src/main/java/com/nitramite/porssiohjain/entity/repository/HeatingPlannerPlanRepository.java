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

import com.nitramite.porssiohjain.entity.HeatingPlannerPlanEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HeatingPlannerPlanRepository extends JpaRepository<HeatingPlannerPlanEntity, Long> {

    Optional<HeatingPlannerPlanEntity> findByPlanVersion(UUID planVersion);

    List<HeatingPlannerPlanEntity> findBySettingsIdAndStatusOrderByCreatedAtDesc(
            Long settingsId, HeatingPlannerPlanStatus status);

    List<HeatingPlannerPlanEntity> findByAccountIdAndSiteIdOrderByCreatedAtDesc(Long accountId, Long siteId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from HeatingPlannerPlanEntity plan
            where plan.horizonEnd < :cutoff
              and plan.status <> :preservedStatus
            """)
    int deleteEndedBeforeAndStatusNot(@Param("cutoff") Instant cutoff,
                                      @Param("preservedStatus") HeatingPlannerPlanStatus preservedStatus);
}
