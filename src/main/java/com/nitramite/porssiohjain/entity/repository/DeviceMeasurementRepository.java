/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.DeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceMeasurementRepository extends JpaRepository<DeviceMeasurementEntity, Long> {
    boolean existsByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKey(
            Long accountId, Long deviceId, DeviceMeasurementType type, String key);

    @Query("""
            select m from DeviceMeasurementEntity m
            where m.account.id = :accountId and m.device.id = :deviceId
              and m.measurementType = :type and m.measurementKey = :key
              and m.observedAt >= :from and m.observedAt < :to
            order by m.observedAt, m.id
            """)
    List<DeviceMeasurementEntity> findAccountHistory(Long accountId, Long deviceId, DeviceMeasurementType type,
                                                      String key, Instant from, Instant to);

    Optional<DeviceMeasurementEntity> findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(
            Long deviceId, DeviceMeasurementType measurementType, String measurementKey);

    Slice<DeviceMeasurementEntity> findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndObservedAtBetweenOrderByObservedAtAscIdAsc(
            Long deviceId, DeviceMeasurementType measurementType, String measurementKey, Instant from, Instant to, Pageable pageable);

    @EntityGraph(attributePaths = "device")
    @Query("""
            select m from DeviceMeasurementEntity m
            where m.account.id = :accountId and m.observedAt >= :observedAfter
              and not exists (
                select newer.id from DeviceMeasurementEntity newer
                where newer.device.id = m.device.id and newer.measurementType = m.measurementType
                  and newer.measurementKey = m.measurementKey
                  and (newer.observedAt > m.observedAt or (newer.observedAt = m.observedAt and newer.id > m.id))
              )
            order by m.observedAt desc, m.id desc
            """)
    List<DeviceMeasurementEntity> findLatestDistinctMeasurements(Long accountId, Instant observedAfter);

    @EntityGraph(attributePaths = "device")
    List<DeviceMeasurementEntity> findTop500ByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(
            Long accountId, Long deviceId, DeviceMeasurementType measurementType, String measurementKey);

    @Modifying
    @Transactional
    @Query("delete from DeviceMeasurementEntity m where m.receivedAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
