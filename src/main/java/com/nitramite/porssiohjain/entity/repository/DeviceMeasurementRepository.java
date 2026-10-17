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

    // Select IDs in one ordered pass through this account's series index, then fetch only the winners and devices.
    @Query(value = """
            select latest.id from (
                select distinct on (device_id, measurement_type, measurement_key)
                    id, device_id, measurement_type, measurement_key, observed_at
                from device_measurement
                where account_id = :accountId and observed_at >= :observedAfter
                order by device_id, measurement_type, measurement_key, observed_at desc, id desc
            ) latest
            """, nativeQuery = true)
    List<Long> findLatestDistinctMeasurementIds(Long accountId, Instant observedAfter);

    @EntityGraph(attributePaths = "device")
    List<DeviceMeasurementEntity> findByIdInOrderByObservedAtDescIdDesc(List<Long> ids);

    default List<DeviceMeasurementEntity> findLatestDistinctMeasurements(Long accountId, Instant observedAfter) {
        List<Long> ids = findLatestDistinctMeasurementIds(accountId, observedAfter);
        return ids.isEmpty() ? List.of() : findByIdInOrderByObservedAtDescIdDesc(ids);
    }

    @EntityGraph(attributePaths = "device")
    List<DeviceMeasurementEntity> findTop500ByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(
            Long accountId, Long deviceId, DeviceMeasurementType measurementType, String measurementKey);

    @Modifying
    @Transactional
    @Query("delete from DeviceMeasurementEntity m where m.receivedAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
