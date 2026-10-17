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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ZigbeeDeviceMeasurementRepository extends JpaRepository<ZigbeeDeviceMeasurementEntity, Long> {
    boolean existsByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKey(
            Long accountId, Long deviceId, ZigbeeMeasurementType type, String key);

    @Query("""
            select m from ZigbeeDeviceMeasurementEntity m
            where m.account.id = :accountId and m.device.id = :deviceId
              and m.measurementType = :type and m.measurementKey = :key
              and m.measuredAt >= :from and m.measuredAt < :to
            order by m.measuredAt, m.id
            """)
    List<ZigbeeDeviceMeasurementEntity> findAccountHistory(Long accountId, Long deviceId, ZigbeeMeasurementType type,
                                                            String key, Instant from, Instant to);


    @EntityGraph(attributePaths = "device")
    List<ZigbeeDeviceMeasurementEntity> findTop500ByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
            Long accountId, Long deviceId, ZigbeeMeasurementType measurementType, String measurementKey);

    Optional<ZigbeeDeviceMeasurementEntity> findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
            Long deviceId,
            ZigbeeMeasurementType measurementType,
            String measurementKey
    );

    Slice<ZigbeeDeviceMeasurementEntity> findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
            Long deviceId, ZigbeeMeasurementType measurementType, String measurementKey, Instant from, Instant to,
            Pageable pageable);

    @EntityGraph(attributePaths = "device")
    List<ZigbeeDeviceMeasurementEntity> findTop500ByAccountIdAndMeasuredAtAfterOrderByMeasuredAtDescIdDesc(
            Long accountId,
            Instant measuredAfter
    );

    // Keep the existing account/IEEE series identity, including devices seen through multiple gateways.
    @Query(value = """
            select latest.id from (
                select distinct on (zigbee_ieee, measurement_type, measurement_key)
                    id, zigbee_ieee, measurement_type, measurement_key, measured_at
                from zigbee_device_measurement
                where account_id = :accountId and measured_at >= :measuredAfter
                order by zigbee_ieee, measurement_type, measurement_key, measured_at desc, id desc
            ) latest
            """, nativeQuery = true)
    List<Long> findLatestDistinctMeasurementIds(Long accountId, Instant measuredAfter);

    @EntityGraph(attributePaths = "device")
    List<ZigbeeDeviceMeasurementEntity> findByIdInOrderByMeasuredAtDescIdDesc(List<Long> ids);

    default List<ZigbeeDeviceMeasurementEntity> findLatestDistinctMeasurements(Long accountId, Instant measuredAfter) {
        List<Long> ids = findLatestDistinctMeasurementIds(accountId, measuredAfter);
        return ids.isEmpty() ? List.of() : findByIdInOrderByMeasuredAtDescIdDesc(ids);
    }

    @EntityGraph(attributePaths = "device")
    List<ZigbeeDeviceMeasurementEntity> findTop500ByAccountIdAndZigbeeIeeeAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
            Long accountId,
            String zigbeeIeee,
            ZigbeeMeasurementType measurementType,
            String measurementKey
    );

    @Modifying
    @Transactional
    @Query("delete from ZigbeeDeviceMeasurementEntity m where m.measuredAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
