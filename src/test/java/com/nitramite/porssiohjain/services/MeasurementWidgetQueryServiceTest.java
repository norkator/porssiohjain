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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.DeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeasurementWidgetQueryServiceTest {
    private final DeviceMeasurementService measurements = mock();
    private final DeviceRepository devices = mock();
    private final MeasurementWidgetQueryService service = new MeasurementWidgetQueryService(measurements, devices);
    private final AccountEntity account = AccountEntity.builder().id(7L).secret("never-return-this").build();
    private final DeviceEntity device = DeviceEntity.builder().id(12L).account(account).deviceName("Bathroom").build();
    private final Instant now = Instant.parse("2026-10-25T10:00:00Z");

    @BeforeEach
    void setup() {
        when(devices.findWithAccountById(12L)).thenReturn(Optional.of(device));
        when(measurements.latestForAccount(7L, Instant.EPOCH)).thenReturn(List.of(row(DevicePlatform.TUYA_SMART, "floor")));
    }

    @Test
    void latestIncludesSilentSensorsAndBothStoresWithoutInventingReportTimes() {
        DeviceMeasurementEntity zigbee = row(DevicePlatform.ANDROID_ZIGBEE, "temperature");
        zigbee.setMeasuredAt(now.minusSeconds(90000));
        when(measurements.latestForAccount(7L, Instant.EPOCH)).thenReturn(List.of(row(DevicePlatform.TUYA_SMART, "floor"), zigbee));
        var result = service.latest(7L);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().measuredAt()).isNull();
        assertThat(result.getFirst().observedAt()).isEqualTo(now.minusSeconds(90000));
        assertThat(result.getLast().source()).isEqualTo("ANDROID_ZIGBEE");
        assertThat(result.getLast().measurementKey()).isEqualTo("temperature");
    }

    @Test
    void historyIsACompleteAccountScoped48HourWindowAcrossDst() {
        Instant from = now.minus(Duration.ofHours(48));
        var rows = IntStream.range(0, 700).mapToObj(i -> {
            var row = row(DevicePlatform.TUYA_SMART, "floor");
            row.setObservedAt(from.plusSeconds(i * 60L));
            return row;
        }).toList();
        when(measurements.accountHistory(7L, 12L, DeviceMeasurementType.TEMPERATURE, "floor", from, now)).thenReturn(rows);
        var result = service.history(7L, 12L, DeviceMeasurementType.TEMPERATURE, "floor", 48, now);
        assertThat(result.points()).hasSize(700);
        assertThat(Duration.between(result.from(), result.to())).isEqualTo(Duration.ofHours(48));
        assertThat(result.latest().measurementKey()).isEqualTo("floor");
        verify(measurements, never()).recentHistory(any(), any(), any(), any());
    }

    @Test
    void foreignOrDeletedDevicesNeverReadHistory() {
        assertThatThrownBy(() -> service.history(99L, 12L, DeviceMeasurementType.TEMPERATURE, "floor", 24, now))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("410");
        assertThatThrownBy(() -> service.history(7L, 999L, DeviceMeasurementType.TEMPERATURE, "floor", 24, now))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("410");
        verifyNoInteractions(measurements);
    }

    @Test
    void latestExcludesRetainedReadingsAfterDeviceChangesOwner() {
        when(measurements.latestForAccount(99L, Instant.EPOCH)).thenReturn(List.of(row(DevicePlatform.TUYA_SMART, "floor")));
        assertThat(service.latest(99L)).isEmpty();
    }

    @Test
    void missingHistoryIsEmptyAndAnotherPropertyIsNeverSubstituted() {
        var history = service.history(7L, 12L, DeviceMeasurementType.TEMPERATURE, "other", 24, now);
        assertThat(history.latest()).isNull();
        assertThat(history.points()).isEmpty();
    }

    @Test
    void invalidWindowsAndMissingAuthenticationFailBeforeQueries() {
        for (int hours : new int[]{0, 1, 23, 25, 49, 168}) {
            assertThatThrownBy(() -> service.history(7L, 12L, DeviceMeasurementType.TEMPERATURE, "floor", hours, now))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        }
        assertThatThrownBy(() -> service.history(7L, 12L, DeviceMeasurementType.TEMPERATURE, " ", 24, now))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.latest(null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        verifyNoInteractions(measurements, devices);
    }

    private DeviceMeasurementEntity row(DevicePlatform source, String key) {
        return DeviceMeasurementEntity.builder().id(1L).account(account).device(device).source(source).sourceKey(key)
                .measurementType(DeviceMeasurementType.TEMPERATURE).measurementKey(key).unit("°C")
                .value(new BigDecimal("22.100")).observedAt(now.minusSeconds(90000)).receivedAt(now).build();
    }
}
