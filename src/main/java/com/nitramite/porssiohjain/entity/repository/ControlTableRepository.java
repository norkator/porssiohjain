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

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import com.nitramite.porssiohjain.entity.enums.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<ControlTableEntity> findByControlIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
            Long controlId, Status status, Instant startTime
    );

    List<ControlTableEntity> findByControlIdAndStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
            Long controlId, Status status, Instant startTime
    );

    List<ControlTableEntity> findByControlIdAndStatusAndStartTimeBetweenOrderByStartTimeAsc(
            Long controlId, Status status, Instant from, Instant to
    );

    @Query("""
            select ct
            from ControlTableEntity ct
            where ct.control.id = :controlId
              and ct.status = :status
              and ct.endTime > :from
              and ct.startTime < :to
            order by ct.startTime asc
            """)
    List<ControlTableEntity> findActivePeriodsOverlapping(
            @Param("controlId") Long controlId,
            @Param("status") Status status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            select count(ct) > 0
            from ControlTableEntity ct
            where ct.control.id = :controlId
              and ct.status = :status
              and ct.startTime <= :time
              and ct.endTime > :time
            """)
    boolean existsActiveAt(
            @Param("controlId") Long controlId,
            @Param("status") Status status,
            @Param("time") Instant time
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ct
            from ControlTableEntity ct
            where ct.control.id = :controlId
              and ct.status = :status
              and ct.startTime <= :time
              and ct.endTime > :time
            order by ct.startTime asc
            """)
    Optional<ControlTableEntity> findFirstActiveAtForUpdate(
            @Param("controlId") Long controlId,
            @Param("status") Status status,
            @Param("time") Instant time
    );

}
