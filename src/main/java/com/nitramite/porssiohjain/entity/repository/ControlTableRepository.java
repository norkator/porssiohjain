/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ControlTableRepository extends JpaRepository<ControlTableEntity, Long> {

    boolean existsByControlAndStartTimeAndEndTime(
            ControlEntity control, Instant start, Instant end
    );

    Optional<ControlTableEntity> findByControlAndStartTimeAndEndTime(
            ControlEntity control,
            Instant startTime,
            Instant endTime
    );

    List<ControlTableEntity> findByControlId(Long controlId);

    void deleteByControlAndStartTimeBetween(ControlEntity control, Instant startTime, Instant endTime);

    List<ControlTableEntity> findByControlIdAndStartTimeAfterOrderByStartTimeAsc(Long controlId, Instant startTime);

    List<ControlTableEntity> findByControlIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long controlId, Instant from, Instant to
    );

}