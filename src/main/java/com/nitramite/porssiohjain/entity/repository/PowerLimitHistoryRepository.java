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

import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import com.nitramite.porssiohjain.entity.PowerLimitHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PowerLimitHistoryRepository extends JpaRepository<PowerLimitHistoryEntity, Long> {

    @Query("""
                SELECT h
                FROM PowerLimitHistoryEntity h
                WHERE h.powerLimit = :powerLimit
                  AND h.createdAt >= :from
                  AND h.createdAt < :to
                ORDER BY h.createdAt DESC
            """)
    Optional<PowerLimitHistoryEntity> findForMinute(
            @Param("powerLimit") PowerLimitEntity powerLimit,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
                SELECT h
                FROM PowerLimitHistoryEntity h
                WHERE h.powerLimit.id = :powerLimitId
                  AND h.powerLimit.account.id = :accountId
                ORDER BY h.createdAt ASC
            """)
    List<PowerLimitHistoryEntity> findAllByPowerLimitAndAccount(
            @Param("accountId") Long accountId,
            @Param("powerLimitId") Long powerLimitId
    );

    @Query("""
                SELECT h
                FROM PowerLimitHistoryEntity h
                WHERE h.powerLimit.id = :powerLimitId
                  AND h.powerLimit.account.id = :accountId
                  AND h.createdAt >= :start
                  AND h.createdAt < :end
            """)
    List<PowerLimitHistoryEntity> findByPowerLimitAndCreatedAtBetween(
            @Param("accountId") Long accountId,
            @Param("powerLimitId") Long powerLimitId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Modifying
    @Query("""
                DELETE FROM PowerLimitHistoryEntity h
                WHERE h.createdAt < :cutoff
            """)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

}