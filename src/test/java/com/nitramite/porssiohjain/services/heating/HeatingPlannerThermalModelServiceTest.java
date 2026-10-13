/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerRoomRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeatingPlannerThermalModelServiceTest {

    private final HeatingPlannerRoomRepository rooms = mock();
    private final ZigbeeDeviceMeasurementRepository measurements = mock();
    private final SiteWeatherRepository forecasts = mock();
    private final HeatingPlannerThermalModelService service = new HeatingPlannerThermalModelService(rooms, measurements, forecasts);
    private final Instant now = Instant.parse("2026-01-15T12:00:00Z");
    private final HeatingPlanSimulationService.ThermalModel configured = new HeatingPlanSimulationService.ThermalModel(
            new BigDecimal("2"), new BigDecimal("0.8"), new BigDecimal("0.06"),
            new BigDecimal("0.012"), new BigDecimal("0.001"));

    @Test
    void estimatesFasterCoolingForLargerIndoorOutdoorDifference() {
        HeatingPlannerThermalModelService service = new HeatingPlannerThermalModelService(mock(), mock(), mock());
        Instant start = Instant.parse("2026-01-15T00:00:00Z");
        List<ZigbeeDeviceMeasurementEntity> temperatures = new ArrayList<>();
        List<SiteWeatherEntity> weather = new ArrayList<>();
        BigDecimal room = new BigDecimal("21.00");
        temperatures.add(temperature(start, room));
        for (int i = 1; i <= 40; i++) {
            BigDecimal outside = i <= 20 ? new BigDecimal("-5.0") : new BigDecimal("-10.0");
            BigDecimal cooling = room.subtract(outside).multiply(new BigDecimal("0.012"));
            room = room.subtract(cooling);
            Instant time = start.plus(Duration.ofHours(i));
            temperatures.add(temperature(time, room));
            weather.add(weather(time.minus(Duration.ofMinutes(30)), outside));
        }

        HeatingPlannerThermalModelService.Estimate estimate = service.estimate(temperatures, weather);

        assertThat(estimate.sampleCount()).isGreaterThanOrEqualTo(35);
        assertThat(estimate.outdoorLossRate()).isBetween(new BigDecimal("0.010"), new BigDecimal("0.014"));
        assertThat(estimate.confidence()).isPositive();
    }

    @Test
    void learnsFromOneMinuteReportsWithoutCountingEveryReportAsAnIndependentInterval() {
        Instant start = now.minus(Duration.ofHours(12));

        var estimate = service.estimate(coolingReadings(start, 720, 0), weatherHistory(start, 720));

        assertThat(estimate.sampleCount()).isEqualTo(48);
        assertThat(estimate.confidence()).isGreaterThan(new BigDecimal("0.25"));
        assertThat(estimate.evidenceAt()).isEqualTo(now);
    }

    @Test
    void flatAndWarmingReadingsDoNotInventCoolingEvidence() {
        Instant start = now.minus(Duration.ofHours(12));
        List<ZigbeeDeviceMeasurementEntity> readings = new ArrayList<>();
        for (int i = 0; i <= 720; i++) {
            readings.add(temperature(start.plusSeconds(i * 60L),
                    new BigDecimal("21").add(BigDecimal.valueOf(Math.max(0, i - 360), 3))));
        }

        var estimate = service.estimate(readings, weatherHistory(start, 720));

        assertThat(estimate.sampleCount()).isZero();
        assertThat(estimate.confidence()).isZero();
        assertThat(estimate.evidenceAt()).isNull();
    }

    @Test
    void ignoresCoolingWithoutMatchingWeatherAndGapsLongerThanTwoHours() {
        List<ZigbeeDeviceMeasurementEntity> readings = List.of(
                temperature(now.minus(Duration.ofHours(4)), new BigDecimal("21")),
                temperature(now, new BigDecimal("20")));

        assertThat(service.estimate(readings, List.of(weather(now, new BigDecimal("-5")))).sampleCount()).isZero();
        var closeReadings = List.of(temperature(now.minusSeconds(900), new BigDecimal("21")),
                temperature(now, new BigDecimal("20.9")));
        assertThat(service.estimate(closeReadings, List.of(weather(now.minus(Duration.ofDays(1)),
                new BigDecimal("-5")))).sampleCount()).isZero();
    }

    @Test
    void stableReadingsPreserveARecentAcceptedModelAndItsOriginalEvidenceTime() {
        var room = learnedRoom();
        Instant trainedAt = room.getModelTrainedAt();
        Instant evidenceAt = room.getModelEvidenceAt();
        Instant start = now.minus(Duration.ofHours(12));
        List<ZigbeeDeviceMeasurementEntity> readings = new ArrayList<>();
        for (int i = 0; i <= 720; i++) readings.add(temperature(start.plusSeconds(i * 60L), new BigDecimal("21")));
        givenTraining(room, readings, weatherHistory(start, 720));

        var result = service.learnAndResolve(7L, 8L, "Kitchen", configured, now);

        assertThat(result.learned()).isTrue();
        assertThat(result.confidence()).isEqualByComparingTo("0.6");
        assertThat(result.sampleCount()).isEqualTo(72);
        assertThat(result.reason()).contains("Retained", "0 trustworthy cooling intervals");
        assertThat(result.model().roomOutdoorLossRate()).isEqualByComparingTo("0.0168");
        assertThat(room.getModelTrainedAt()).isEqualTo(trainedAt);
        assertThat(room.getModelEvidenceAt()).isEqualTo(evidenceAt);
        verify(rooms, never()).save(any());
    }

    @Test
    void missingWeatherPreservesARecentModelWithoutFetchingUnmatchableMeasurements() {
        var room = learnedRoom();
        givenTraining(room, List.of(), List.of());

        var result = service.learnAndResolve(7L, 8L, "Kitchen", configured, now);

        assertThat(result.confidence()).isEqualByComparingTo("0.6");
        assertThat(result.reason()).contains("Retained", "No retained weather");
        verifyNoInteractions(measurements);
        verify(rooms, never()).save(any());
    }

    @Test
    void aLowConfidenceCandidateDoesNotReplaceARecentAcceptedModel() {
        var room = learnedRoom();
        Instant start = now.minus(Duration.ofHours(6));
        givenTraining(room, coolingReadings(start, 360, 0), weatherHistory(start, 360));

        var result = service.learnAndResolve(7L, 8L, "Kitchen", configured, now);

        assertThat(result.confidence()).isEqualByComparingTo("0.6");
        assertThat(result.reason()).contains("Retained", "24 trustworthy cooling intervals");
        assertThat(room.getModelSampleCount()).isEqualTo(72);
        verify(rooms, never()).save(any());
    }

    @Test
    void expiredModelCannotBeRetainedWhenCoolingEvidenceIsMissing() {
        var room = learnedRoom();
        room.setModelEvidenceAt(now.minus(Duration.ofDays(14)).minusSeconds(1));
        room.setModelTrainedAt(now);
        givenTraining(room, List.of(), List.of());

        var result = service.learnAndResolve(7L, 8L, "Kitchen", configured, now);

        assertThat(result.learned()).isFalse();
        assertThat(result.confidence()).isZero();
        assertThat(room.isModelParametersLearned()).isFalse();
        assertThat(room.getModelEvidenceAt()).isNull();
        verify(rooms).save(room);
    }

    @Test
    void recalculatingTheSameHistoryDoesNotRenewTheEvidenceAge() {
        var room = newRoom();
        Instant evidenceAt = now.minus(Duration.ofDays(3));
        Instant start = evidenceAt.minus(Duration.ofHours(8));
        givenTraining(room, coolingReadings(start, 480, 0), weatherHistory(start, 480));

        service.learnAndResolve(7L, 8L, "Kitchen", configured, now);
        service.learnAndResolve(7L, 8L, "Kitchen", configured, now.plus(Duration.ofDays(1)));

        assertThat(room.getModelConfidence()).isGreaterThan(new BigDecimal("0.25"));
        assertThat(room.getModelEvidenceAt()).isEqualTo(evidenceAt);
    }

    @Test
    void rebuildingFromZeroReadsPastTheFirstThousandRowsToReachRecentCooling() {
        var room = newRoom();
        Instant start = now.minus(Duration.ofHours(30));
        var readings = coolingReadings(start, 1800, 1000);
        givenTraining(room, List.of(), weatherHistory(start, 1800));
        when(rooms.findByAccountIdAndSiteIdOrderBySortOrderAscIdAsc(7L, 8L)).thenReturn(List.of(room));
        when(measurements.findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
                eq(21L), eq(ZigbeeMeasurementType.TEMPERATURE), eq("temperature"), any(), any(), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(readings.subList(0, 1000), PageRequest.of(0, 1000), true),
                        new SliceImpl<>(readings.subList(1000, readings.size()), PageRequest.of(1, 1000), false));

        var results = service.rebuildModels(7L, 8L, now);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().reason()).contains("Rebuilt");
        assertThat(room.isModelParametersLearned()).isTrue();
        assertThat(room.getModelSampleCount()).isGreaterThanOrEqualTo(50);
        assertThat(room.getModelConfidence()).isGreaterThan(new BigDecimal("0.25"));
        assertThat(room.getModelEvidenceAt()).isEqualTo(now);
        verify(measurements).findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
                eq(21L), eq(ZigbeeMeasurementType.TEMPERATURE), eq("temperature"), any(), eq(now), eq(PageRequest.of(1, 1000)));
    }

    @Test
    void startsTrainingWithinAvailableWeatherHistory() {
        var room = newRoom();
        Instant firstWeather = now.minus(Duration.ofDays(3));
        givenTraining(room, List.of(), List.of(weather(firstWeather, new BigDecimal("-5"))));

        service.learnAndResolve(7L, 8L, "Kitchen", configured, now);

        verify(measurements).findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
                eq(21L), eq(ZigbeeMeasurementType.TEMPERATURE), eq("temperature"),
                eq(firstWeather.minus(Duration.ofMinutes(90))), eq(now), any(Pageable.class));
    }

    @Test
    void inspectionReadsOnlyTheRequestedAccountsSavedRoomModelsWithoutRebuilding() {
        var room = learnedRoom();
        when(rooms.findByAccountIdAndSiteIdOrderBySortOrderAscIdAsc(7L, 8L)).thenReturn(List.of(room));

        var statuses = service.modelStatuses(7L, 8L, now);

        assertThat(statuses).hasSize(1);
        assertThat(statuses.getFirst().confidence()).isEqualByComparingTo("0.6");
        assertThat(statuses.getFirst().sampleCount()).isEqualTo(72);
        assertThat(statuses.getFirst().status()).contains("Meets the 25%");
        verifyNoInteractions(measurements, forecasts);
        verify(rooms, never()).save(any());
    }

    @Test
    void cannotLearnFromAnotherAccountsRoom() {
        when(rooms.findByAccountIdAndSiteIdAndNameIgnoreCase(9L, 8L, "Kitchen")).thenReturn(Optional.empty());

        var result = service.learnAndResolve(9L, 8L, "Kitchen", configured, now);

        assertThat(result.learned()).isFalse();
        verifyNoInteractions(measurements, forecasts);
    }

    private HeatingPlannerRoomEntity newRoom() {
        return HeatingPlannerRoomEntity.builder().id(10L).name("Kitchen")
                .roomSensorDevice(DeviceEntity.builder().id(21L).build()).build();
    }

    private HeatingPlannerRoomEntity learnedRoom() {
        var room = newRoom();
        room.setModelParametersLearned(true);
        room.setModelConfidence(new BigDecimal("0.6"));
        room.setModelSampleCount(72);
        room.setModelTrainedAt(now.minus(Duration.ofHours(12)));
        room.setModelEvidenceAt(now.minus(Duration.ofDays(1)));
        room.setRoomOutdoorLossRate(new BigDecimal("0.020"));
        room.setWindLossRate(new BigDecimal("0.002"));
        return room;
    }

    private void givenTraining(HeatingPlannerRoomEntity room, List<ZigbeeDeviceMeasurementEntity> readings,
                               List<SiteWeatherEntity> weather) {
        when(rooms.findByAccountIdAndSiteIdAndNameIgnoreCase(7L, 8L, "Kitchen")).thenReturn(Optional.of(room));
        when(forecasts.findBySiteAndForecastTimeBetweenOrderByForecastTimeAsc(eq(room.getSite()), any(), any()))
                .thenReturn(weather);
        when(measurements.findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
                eq(21L), eq(ZigbeeMeasurementType.TEMPERATURE), eq("temperature"), any(), any(), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(readings));
    }

    private List<ZigbeeDeviceMeasurementEntity> coolingReadings(Instant start, int minutes, int stableMinutes) {
        List<ZigbeeDeviceMeasurementEntity> readings = new ArrayList<>();
        for (int i = 0; i <= minutes; i++) readings.add(temperature(start.plusSeconds(i * 60L),
                new BigDecimal("21").subtract(new BigDecimal("0.005").multiply(BigDecimal.valueOf(Math.max(0, i - stableMinutes))))));
        return readings;
    }

    private List<SiteWeatherEntity> weatherHistory(Instant start, int minutes) {
        List<SiteWeatherEntity> weather = new ArrayList<>();
        for (int i = 0; i <= minutes; i += 60) weather.add(weather(start.plusSeconds(i * 60L), new BigDecimal("-5")));
        return weather;
    }

    private ZigbeeDeviceMeasurementEntity temperature(Instant time, BigDecimal value) {
        return ZigbeeDeviceMeasurementEntity.builder().measuredAt(time).value(value).build();
    }

    private SiteWeatherEntity weather(Instant time, BigDecimal temperature) {
        return SiteWeatherEntity.builder().forecastTime(time).temperature(temperature)
                .windSpeedMs(BigDecimal.ZERO).build();
    }
}
