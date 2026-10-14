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
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HeatingPlannerPlanPointRepository extends JpaRepository<HeatingPlannerPlanPointEntity, Long> {

    List<HeatingPlannerPlanPointEntity> findByRoomIdAndPlannedTimeBetweenOrderByPlannedTimeAsc(
            Long roomId, Instant start, Instant end);

    List<HeatingPlannerPlanPointEntity> findByAccountIdAndSiteIdAndStatusAndPlannedTimeBetweenOrderByPlannedTimeAsc(
            Long accountId, Long siteId, HeatingPlannerPlanPointStatus status, Instant start, Instant end);

    List<HeatingPlannerPlanPointEntity> findByPlanVersion(UUID planVersion);

    List<HeatingPlannerPlanPointEntity> findByPlanVersionAndRoomIdAndPlannedTimeBetweenOrderByPlannedTimeAsc(
            UUID planVersion, Long roomId, Instant start, Instant end);

    /** Latest prediction available when each period began, including superseded plans. */
    @Query("""
            select point from HeatingPlannerPlanPointEntity point
            join fetch point.room
            join fetch point.plan
            where point.account.id = :accountId and point.site.id = :siteId
              and point.plannedTime >= :start and point.plannedTime < :end
              and point.plan.createdAt <= point.plannedTime
              and not exists (
                  select newer.id from HeatingPlannerPlanPointEntity newer
                  where newer.account.id = :accountId and newer.site.id = :siteId
                    and newer.room.id = point.room.id and newer.plannedTime = point.plannedTime
                    and newer.plan.createdAt <= point.plannedTime
                    and (newer.plan.createdAt > point.plan.createdAt
                         or (newer.plan.createdAt = point.plan.createdAt and newer.id > point.id))
              )
            order by point.plannedTime asc
            """)
    List<HeatingPlannerPlanPointEntity> findLatestHistoricalPredictions(
            @Param("accountId") Long accountId, @Param("siteId") Long siteId,
            @Param("start") Instant start, @Param("end") Instant end);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from HeatingPlannerPlanPointEntity point
            where point.plan.id in (
                select plan.id from HeatingPlannerPlanEntity plan
                where plan.horizonEnd < :cutoff
                  and plan.status <> :preservedStatus
            )
            """)
    int deleteByPlanEndedBeforeAndPlanStatusNot(@Param("cutoff") Instant cutoff,
                                                @Param("preservedStatus") HeatingPlannerPlanStatus preservedStatus);
}
