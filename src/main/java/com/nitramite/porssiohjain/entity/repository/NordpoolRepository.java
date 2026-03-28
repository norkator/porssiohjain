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

import com.nitramite.porssiohjain.entity.NordpoolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NordpoolRepository extends JpaRepository<NordpoolEntity, Long> {

    List<NordpoolEntity> findByDeliveryStartBetween(Instant start, Instant end);

    @Query("SELECT n FROM NordpoolEntity n " +
            "WHERE n.deliveryStart >= :start AND n.deliveryStart <= :end " +
            "ORDER BY n.deliveryStart ASC")
    List<NordpoolEntity> findPricesBetween(@Param("start") Instant start, @Param("end") Instant end);

    Optional<NordpoolEntity> findFirstByDeliveryStartLessThanEqualAndDeliveryEndGreaterThan(
            Instant deliveryStart,
            Instant deliveryEnd
    );

    boolean existsByDeliveryStartBetween(Instant start, Instant end);

    void deleteByDeliveryStartBefore(Instant cutoff);

}
