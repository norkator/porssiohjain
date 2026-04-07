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
