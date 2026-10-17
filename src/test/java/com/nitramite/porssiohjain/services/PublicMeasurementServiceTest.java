package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.entity.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicMeasurementServiceTest {
    private final AccountRepository accounts = mock();
    private final DeviceRepository devices = mock();
    private final PublicMeasurementRepository publications = mock();
    private final DeviceMeasurementService measurements = mock();
    private final DemoAccountGuard demo = mock();
    private final PublicMeasurementService service = new PublicMeasurementService(accounts, devices, publications, measurements, demo);
    private final UUID pageId = UUID.randomUUID();
    private final AccountEntity owner = AccountEntity.builder().id(7L).uuid(UUID.randomUUID()).secret("private-secret").build();
    private final DeviceEntity device = DeviceEntity.builder().id(12L).account(owner).deviceName("Garden")
            .timezone("America/Los_Angeles").build();
    private final PublicMeasurementEntity humidity = PublicMeasurementEntity.builder().id(42L).account(owner).device(device)
            .measurementType(DeviceMeasurementType.HUMIDITY).measurementKey("humidity").build();
    private final Instant now = Instant.parse("2026-10-25T10:00:00Z");

    @BeforeEach
    void setup() {
        when(accounts.findWithLockById(7L)).thenReturn(Optional.of(owner));
        when(devices.findByIdAndAccount(12L, owner)).thenReturn(Optional.of(device));
        when(measurements.existsForAccount(7L, 12L, DeviceMeasurementType.HUMIDITY, "humidity")).thenReturn(true);
        when(accounts.findByPublicMeasurementsUuidAndBlockedFalse(pageId)).thenReturn(Optional.of(owner));
    }

    @Test
    void publishingCreatesSeparateStablePageIdentifierAndOnlyTheSelectedProperty() {
        service.setPublished(7L, 12L, DeviceMeasurementType.HUMIDITY, "humidity", true);
        UUID token = owner.getPublicMeasurementsUuid();
        assertThat(token).isNotNull().isNotEqualTo(owner.getUuid());
        assertThat(owner.getSecret()).isEqualTo("private-secret");
        var saved = ArgumentCaptor.forClass(PublicMeasurementEntity.class);
        verify(publications).save(saved.capture());
        assertThat(saved.getValue().getMeasurementType()).isEqualTo(DeviceMeasurementType.HUMIDITY);
        assertThat(saved.getValue().getMeasurementKey()).isEqualTo("humidity");
        assertThat(saved.getValue().getAccount()).isSameAs(owner);
        assertThat(saved.getValue().getDevice()).isSameAs(device);
        when(publications.findByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKey(
                7L, 12L, DeviceMeasurementType.HUMIDITY, "humidity")).thenReturn(Optional.of(humidity));
        service.setPublished(7L, 12L, DeviceMeasurementType.HUMIDITY, "humidity", true);
        assertThat(owner.getPublicMeasurementsUuid()).isEqualTo(token);
        verify(publications, times(1)).save(any());
        verify(demo, times(2)).assertWritable(7L);
    }

    @Test
    void foreignDevicesAndUnrecordedPropertiesCannotBePublished() {
        assertThatThrownBy(() -> service.setPublished(7L, 99L, DeviceMeasurementType.HUMIDITY, "humidity", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setPublished(7L, 12L, DeviceMeasurementType.TEMPERATURE, "temperature", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(owner.getPublicMeasurementsUuid()).isNull();
        verify(publications, never()).save(any());
    }

    @Test
    void removingPublicationNeedsNoRetainedMeasurementAndKeepsTheSameLink() {
        owner.setPublicMeasurementsUuid(pageId);
        when(publications.findByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKey(
                7L, 12L, DeviceMeasurementType.HUMIDITY, "humidity")).thenReturn(Optional.of(humidity));
        service.setPublished(7L, 12L, DeviceMeasurementType.HUMIDITY, "humidity", false);
        verify(publications).delete(humidity);
        verifyNoInteractions(devices);
        verify(measurements, never()).existsForAccount(anyLong(), anyLong(), any(), anyString());
        assertThat(owner.getPublicMeasurementsUuid()).isEqualTo(pageId);
    }

    @Test
    void demoAndBlockedAccountsCannotPublish() {
        doThrow(new DemoAccountMutationException()).when(demo).assertWritable(7L);
        assertThatThrownBy(() -> service.setPublished(7L, 12L, DeviceMeasurementType.HUMIDITY, "humidity", true))
                .isInstanceOf(DemoAccountMutationException.class);
        verifyNoInteractions(publications, devices);
        reset(demo);
        owner.setBlocked(true);
        assertThatThrownBy(() -> service.setPublished(7L, 12L, DeviceMeasurementType.HUMIDITY, "humidity", true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(publications, never()).save(any());
    }

    @Test
    void overviewLoadsOneCompleteRollingDayForPublishedSeriesEvenAcrossDst() {
        when(publications.findVisibleForAccount(7L)).thenReturn(List.of(humidity));
        Instant from = now.minus(Duration.ofHours(24));
        when(measurements.accountHistory(7L, 12L, DeviceMeasurementType.HUMIDITY, "humidity", from, now))
                .thenReturn(List.of(reading(1L, from)));
        var page = service.publicPage(pageId, null, 1, now).orElseThrow();
        assertThat(page.from()).isEqualTo(from);
        assertThat(page.to()).isEqualTo(now);
        assertThat(page.series()).hasSize(1);
        var series = page.series().getFirst();
        assertThat(series.id()).isEqualTo(42L);
        assertThat(series.deviceName()).isEqualTo("Garden");
        assertThat(series.unit()).isEqualTo("%");
        assertThat(series.timezone()).isEqualTo("America/Los_Angeles");
        assertThat(series.includesCloudSnapshots()).isTrue();
        verify(publications, never()).findByAccountIdOrderByIdAsc(anyLong());
    }

    @Test
    void weekDetailIsPublicationScopedAndKeepsMoreThanFiveHundredReadings() {
        Instant from = now.minus(Duration.ofDays(7));
        when(publications.findVisibleById(7L, 42L)).thenReturn(Optional.of(humidity));
        var history = IntStream.range(0, 1008).mapToObj(i -> reading((long) i, from.plusSeconds(i * 600L))).toList();
        when(measurements.accountHistory(7L, 12L, DeviceMeasurementType.HUMIDITY, "humidity", from, now)).thenReturn(history);
        var page = service.publicPage(pageId, 42L, 7, now).orElseThrow();
        assertThat(page.series().getFirst().readings()).hasSize(1008);
        assertThat(page.series().getFirst().readings().getFirst().observedAt()).isEqualTo(from);
        assertThat(service.publicPage(pageId, 99L, 7, now)).isEmpty();
        verify(publications, never()).findVisibleForAccount(anyLong());
    }

    @Test
    void missingPagesUnpublishedItemsAndInvalidRangesDoNotReadAnyHistory() {
        assertThat(service.publicPage(UUID.randomUUID(), null, 1, now)).isEmpty();
        assertThat(service.publicPage(pageId, 99L, 7, now)).isEmpty();
        assertThat(service.publicPage(pageId, 42L, 8, now)).isEmpty();
        assertThat(service.publicPage(pageId, 42L, 0, now)).isEmpty();
        assertThat(service.publicPage(pageId, null, 7, now)).isEmpty();
        assertThat(service.publicPage(null, null, 1, now)).isEmpty();
        verify(measurements, never()).accountHistory(anyLong(), anyLong(), any(), anyString(), any(), any());
    }

    @Test
    void ownerCanManagePublishedSourcesAfterTheirHistoryExpires() {
        owner.setPublicMeasurementsUuid(pageId);
        when(accounts.findById(7L)).thenReturn(Optional.of(owner));
        when(publications.findByAccountIdOrderByIdAsc(7L)).thenReturn(List.of(humidity));
        var settings = service.ownerSettings(7L);
        assertThat(settings.pageId()).isEqualTo(pageId);
        assertThat(settings.selections()).containsExactly(new PublicMeasurementService.PublishedMeasurement(
                12L, "Garden", DeviceMeasurementType.HUMIDITY, "humidity"));
        verifyNoInteractions(measurements);
    }

    private DeviceMeasurementEntity reading(Long id, Instant at) {
        return DeviceMeasurementEntity.builder().id(id).observedAt(at).value(new BigDecimal("45.5")).unit("%").build();
    }
}
