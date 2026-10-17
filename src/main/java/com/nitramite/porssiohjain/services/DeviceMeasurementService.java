/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

/** A common read contract over existing Zigbee history and new integration measurements. No dual writes. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceMeasurementService {
    private final DeviceMeasurementRepository measurements;
    private final ZigbeeDeviceMeasurementRepository zigbeeMeasurements;

    public Optional<DeviceMeasurementEntity> latest(DeviceEntity device, DeviceMeasurementType type, String key) {
        if (device.getDevicePlatform() == DevicePlatform.ANDROID_ZIGBEE) {
            return zigbeeMeasurements.findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                    device.getId(), ZigbeeMeasurementType.valueOf(type.name()), key).map(this::fromZigbee);
        }
        return measurements.findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(
                device.getId(), type, key);
    }

    public Slice<DeviceMeasurementEntity> history(DeviceEntity device, DeviceMeasurementType type, String key,
                                                  Instant from, Instant to, Pageable page) {
        if (device.getDevicePlatform() == DevicePlatform.ANDROID_ZIGBEE) {
            return zigbeeMeasurements.findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
                    device.getId(), ZigbeeMeasurementType.valueOf(type.name()), key, from, to, page).map(this::fromZigbee);
        }
        return measurements.findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndObservedAtBetweenOrderByObservedAtAscIdAsc(
                device.getId(), type, key, from, to, page);
    }

    public List<DeviceMeasurementEntity> latestForAccount(Long accountId, Instant after) {
        return Stream.concat(measurements.findLatestDistinctMeasurements(accountId, after).stream(),
                        zigbeeMeasurements.findLatestDistinctMeasurements(accountId, after).stream().map(this::fromZigbee))
                .sorted(Comparator.comparing(DeviceMeasurementEntity::getObservedAt).reversed()).toList();
    }

    public List<DeviceMeasurementEntity> recentHistory(Long accountId, Long deviceId, DeviceMeasurementType type, String key) {
        return Stream.concat(
                        measurements.findTop500ByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(
                                accountId, deviceId, type, key).stream(),
                        zigbeeMeasurements.findTop500ByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                                accountId, deviceId, ZigbeeMeasurementType.valueOf(type.name()), key).stream().map(this::fromZigbee))
                .sorted(Comparator.comparing(DeviceMeasurementEntity::getObservedAt).reversed()).limit(500).toList();
    }

    public boolean existsForAccount(Long accountId, Long deviceId, DeviceMeasurementType type, String key) {
        return measurements.existsByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKey(accountId, deviceId, type, key)
                || zigbeeMeasurements.existsByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKey(
                accountId, deviceId, ZigbeeMeasurementType.valueOf(type.name()), key);
    }

    /** Account-scoped, complete time window for public charts; never applies the recent-history 500-row cap. */
    public List<DeviceMeasurementEntity> accountHistory(Long accountId, Long deviceId, DeviceMeasurementType type,
                                                       String key, Instant from, Instant to) {
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("History window must be positive and at most seven days");
        }
        return Stream.concat(measurements.findAccountHistory(accountId, deviceId, type, key, from, to).stream(),
                        zigbeeMeasurements.findAccountHistory(accountId, deviceId, ZigbeeMeasurementType.valueOf(type.name()),
                                key, from, to).stream().map(this::fromZigbee))
                .sorted(Comparator.comparing(DeviceMeasurementEntity::getObservedAt)
                        .thenComparing(DeviceMeasurementEntity::getId)).toList();
    }

    private DeviceMeasurementEntity fromZigbee(ZigbeeDeviceMeasurementEntity row) {
        DeviceMeasurementType type = DeviceMeasurementType.valueOf(row.getMeasurementType().name());
        return DeviceMeasurementEntity.builder().id(row.getId()).account(row.getAccount()).device(row.getDevice())
                .source(DevicePlatform.ANDROID_ZIGBEE).sourceKey(row.getMeasurementKey())
                .measurementType(type).measurementKey(row.getMeasurementKey()).value(row.getValue())
                .unit(type == DeviceMeasurementType.TEMPERATURE || type == DeviceMeasurementType.THERMOSTAT_SETPOINT ? "°C" : "%")
                .measuredAt(row.getMeasuredAt()).observedAt(row.getMeasuredAt()).receivedAt(row.getReceivedAt()).build();
    }
}
