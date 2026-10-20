/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.HeatingPlannerComfortScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HeatingPlannerComfortScheduleRepository extends JpaRepository<HeatingPlannerComfortScheduleEntity, Long> {
    List<HeatingPlannerComfortScheduleEntity> findByAccountIdAndSiteIdOrderByNameAsc(Long accountId, Long siteId);
    Optional<HeatingPlannerComfortScheduleEntity> findByIdAndAccountIdAndSiteId(Long id, Long accountId, Long siteId);
}
