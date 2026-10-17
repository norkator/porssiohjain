/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.PublicMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.PublicMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicMeasurementService {
    private final AccountRepository accounts;
    private final DeviceRepository devices;
    private final PublicMeasurementRepository publications;
    private final DeviceMeasurementService measurements;
    private final DemoAccountGuard demoGuard;

    public OwnerSettings ownerSettings(Long accountId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return new OwnerSettings(account.getPublicMeasurementsUuid(), publications.findByAccountIdOrderByIdAsc(accountId)
                .stream().map(this::selection).toList());
    }

    @Transactional
    public void setPublished(Long accountId, Long deviceId, DeviceMeasurementType type, String key, boolean published) {
        demoGuard.assertWritable(accountId);
        // Serialize first publication/link creation and duplicate checkbox requests for this account.
        var account = accounts.findWithLockById(accountId)
                .filter(a -> !a.isBlocked()).orElseThrow(() -> new IllegalArgumentException("Account unavailable"));
        if (type == null || key == null || key.isBlank() || key.length() > 64) {
            throw new IllegalArgumentException("Invalid measurement");
        }
        var existing = publications.findByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKey(accountId, deviceId, type, key);
        if (!published) {
            existing.ifPresent(publications::delete);
            return;
        }
        var device = devices.findByIdAndAccount(deviceId, account)
                .orElseThrow(() -> new IllegalArgumentException("Device not owned by account"));
        if (!measurements.existsForAccount(accountId, deviceId, type, key)) {
            throw new IllegalArgumentException("Measurement not found");
        }
        if (account.getPublicMeasurementsUuid() == null) {
            account.setPublicMeasurementsUuid(UUID.randomUUID());
            accounts.save(account);
        }
        if (existing.isEmpty()) {
            publications.save(PublicMeasurementEntity.builder().account(account).device(device)
                    .measurementType(type).measurementKey(key).build());
        }
    }

    /** Only published series are resolved; no account entity or device credentials leave this service. */
    public Optional<PublicPage> publicPage(UUID pageId, Long measurementId, int days, Instant now) {
        if (pageId == null || days < 1 || days > 7 || (measurementId == null && days != 1)) {
            return Optional.empty();
        }
        return accounts.findByPublicMeasurementsUuidAndBlockedFalse(pageId).flatMap(account -> {
            List<PublicMeasurementEntity> selected;
            if (measurementId == null) {
                selected = publications.findVisibleForAccount(account.getId());
            } else {
                var item = publications.findVisibleById(account.getId(), measurementId);
                if (item.isEmpty()) return Optional.empty();
                selected = List.of(item.get());
            }
            Instant from = now.minus(Duration.ofDays(days));
            List<PublicSeries> series = selected.stream().map(item -> {
                var history = measurements.accountHistory(account.getId(), item.getDevice().getId(),
                        item.getMeasurementType(), item.getMeasurementKey(), from, now);
                List<Reading> readings = history.stream().map(row -> new Reading(row.getObservedAt(), row.getValue())).toList();
                String unit = history.isEmpty() ? switch (item.getMeasurementType()) {
                    case TEMPERATURE, THERMOSTAT_SETPOINT -> "°C";
                    case HUMIDITY, BATTERY_PERCENTAGE -> "%";
                } : history.getLast().getUnit();
                return new PublicSeries(item.getId(), item.getDevice().getDeviceName(), item.getMeasurementType(),
                        item.getMeasurementKey(), unit, validZone(item.getDevice().getTimezone()), readings,
                        history.stream().anyMatch(row -> row.getMeasuredAt() == null));
            }).toList();
            return Optional.of(new PublicPage(pageId, from, now, series));
        });
    }

    private PublishedMeasurement selection(PublicMeasurementEntity item) {
        return new PublishedMeasurement(item.getDevice().getId(), item.getDevice().getDeviceName(),
                item.getMeasurementType(), item.getMeasurementKey());
    }

    private String validZone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (RuntimeException exception) {
            return "Europe/Helsinki";
        }
    }

    public record OwnerSettings(UUID pageId, List<PublishedMeasurement> selections) {}
    public record PublishedMeasurement(Long deviceId, String deviceName, DeviceMeasurementType type, String key) {}
    public record Reading(Instant observedAt, BigDecimal value) {}
    public record PublicSeries(Long id, String deviceName, DeviceMeasurementType type, String key, String unit,
                               String timezone, List<Reading> readings, boolean includesCloudSnapshots) {}
    public record PublicPage(UUID id, Instant from, Instant to, List<PublicSeries> series) {}
}
