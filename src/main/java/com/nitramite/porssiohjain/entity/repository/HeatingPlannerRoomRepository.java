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

import com.nitramite.porssiohjain.entity.HeatingPlannerRoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HeatingPlannerRoomRepository extends JpaRepository<HeatingPlannerRoomEntity, Long> {

    @Query("""
            select count(room) > 0 from HeatingPlannerRoomEntity room join room.heatSources source
            where room.account.id = :accountId and room.site.id <> :siteId
              and source.controllingDevice.id = :deviceId
            """)
    boolean isControllerAssignedToAnotherSite(@Param("accountId") Long accountId,
                                               @Param("siteId") Long siteId,
                                               @Param("deviceId") Long deviceId);

    List<HeatingPlannerRoomEntity> findBySettingsIdOrderBySortOrderAscIdAsc(Long settingsId);

    List<HeatingPlannerRoomEntity> findByAccountIdAndSiteIdOrderBySortOrderAscIdAsc(Long accountId, Long siteId);

    Optional<HeatingPlannerRoomEntity> findByIdAndAccountId(Long id, Long accountId);

    Optional<HeatingPlannerRoomEntity> findByAccountIdAndSiteIdAndNameIgnoreCase(Long accountId, Long siteId, String name);
}
