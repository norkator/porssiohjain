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

import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ZigbeeDeviceMeasurementRepository extends JpaRepository<ZigbeeDeviceMeasurementEntity, Long> {

    Optional<ZigbeeDeviceMeasurementEntity> findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
            Long deviceId,
            ZigbeeMeasurementType measurementType,
            String measurementKey
    );

    @EntityGraph(attributePaths = "device")
    List<ZigbeeDeviceMeasurementEntity> findTop500ByAccountIdAndMeasuredAtAfterOrderByMeasuredAtDescIdDesc(
            Long accountId,
            Instant measuredAfter
    );

    @Modifying
    @Transactional
    @Query("delete from ZigbeeDeviceMeasurementEntity m where m.measuredAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
