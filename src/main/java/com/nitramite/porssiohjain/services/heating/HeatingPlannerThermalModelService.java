/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.HeatingPlannerRoomEntity;
import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerRoomRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HeatingPlannerThermalModelService {

    private static final Duration TRAINING_WINDOW = Duration.ofDays(21);
    private static final Duration MAX_SAMPLE_GAP = Duration.ofHours(2);
    private static final Duration MAX_WEATHER_GAP = Duration.ofMinutes(90);
    private static final int MIN_SAMPLES = 24;

    private final HeatingPlannerRoomRepository roomRepository;
    private final ZigbeeDeviceMeasurementRepository measurementRepository;
    private final SiteWeatherRepository weatherRepository;

    @Transactional
    public ModelResolution learnAndResolve(Long accountId, Long siteId, String roomName,
                                           HeatingPlanSimulationService.ThermalModel configured, Instant now) {
        HeatingPlannerRoomEntity room = roomRepository
                .findByAccountIdAndSiteIdAndNameIgnoreCase(accountId, siteId, roomName)
                .orElse(null);
        if (room == null || room.getRoomSensorDevice() == null) {
            return ModelResolution.configured(configured, "No persisted room sensor is available for learning");
        }
        String key = room.getRoomSensorMeasurementKey();
        if (key == null || key.isBlank()) key = HeatingPlannerMeasurementService.DEFAULT_TEMPERATURE_KEY;
        Instant from = now.minus(TRAINING_WINDOW);
        List<ZigbeeDeviceMeasurementEntity> temperatures = measurementRepository
                .findTop1000ByDeviceIdAndMeasurementTypeAndMeasurementKeyAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
                        room.getRoomSensorDevice().getId(), ZigbeeMeasurementType.TEMPERATURE, key, from, now);
        List<SiteWeatherEntity> weather = weatherRepository
                .findBySiteAndForecastTimeBetweenOrderByForecastTimeAsc(room.getSite(), from, now);
        Estimate estimate = estimate(temperatures, weather);
        if (estimate.sampleCount() < MIN_SAMPLES) {
            updateMetadata(room, estimate, false, now);
            return new ModelResolution(configured, false, estimate.sampleCount(), BigDecimal.ZERO,
                    "Configured model: fewer than " + MIN_SAMPLES + " trustworthy cooling intervals");
        }

        BigDecimal confidence = estimate.confidence();
        BigDecimal learnedOutdoor = blend(configured.roomOutdoorLossRate(), estimate.outdoorLossRate(), confidence);
        BigDecimal learnedWind = blend(configured.windLossRate(), estimate.windLossRate(), confidence);
        HeatingPlanSimulationService.ThermalModel resolved = new HeatingPlanSimulationService.ThermalModel(
                configured.heaterPowerKw(), configured.floorHeatingRate(), configured.floorToRoomRate(),
                learnedOutdoor, learnedWind);
        room.setRoomOutdoorLossRate(estimate.outdoorLossRate());
        room.setWindLossRate(estimate.windLossRate());
        updateMetadata(room, estimate, true, now);
        return new ModelResolution(resolved, true, estimate.sampleCount(), confidence,
                "Observed cooling model blended with configured defaults according to confidence");
    }

    Estimate estimate(List<ZigbeeDeviceMeasurementEntity> temperatures, List<SiteWeatherEntity> weather) {
        List<CoolingSample> samples = new ArrayList<>();
        for (int i = 1; i < temperatures.size(); i++) {
            ZigbeeDeviceMeasurementEntity before = temperatures.get(i - 1);
            ZigbeeDeviceMeasurementEntity after = temperatures.get(i);
            Duration elapsed = Duration.between(before.getMeasuredAt(), after.getMeasuredAt());
            if (elapsed.toMinutes() < 5 || elapsed.compareTo(MAX_SAMPLE_GAP) > 0) continue;
            BigDecimal change = after.getValue().subtract(before.getValue());
            if (change.signum() >= 0 || change.abs().compareTo(new BigDecimal("3.0")) > 0) continue;
            SiteWeatherEntity nearest = nearestWeather(weather, midpoint(before.getMeasuredAt(), after.getMeasuredAt()));
            if (nearest == null || nearest.getTemperature() == null) continue;
            BigDecimal hours = BigDecimal.valueOf(elapsed.toMinutes()).divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
            BigDecimal coolingPerHour = change.negate().divide(hours, 8, RoundingMode.HALF_UP);
            BigDecimal averageRoom = before.getValue().add(after.getValue()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            BigDecimal deltaOutdoor = averageRoom.subtract(nearest.getTemperature()).max(BigDecimal.ZERO);
            BigDecimal wind = nearest.getWindSpeedMs() == null ? BigDecimal.ZERO : nearest.getWindSpeedMs().max(BigDecimal.ZERO);
            if (deltaOutdoor.signum() > 0) samples.add(new CoolingSample(deltaOutdoor, wind, coolingPerHour));
        }
        if (samples.isEmpty()) return new Estimate(BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO);

        double xx = 0, xw = 0, ww = 0, xy = 0, wy = 0;
        for (CoolingSample sample : samples) {
            double x = sample.outdoorDelta().doubleValue();
            double w = sample.wind().doubleValue();
            double y = sample.coolingPerHour().doubleValue();
            xx += x * x; xw += x * w; ww += w * w; xy += x * y; wy += w * y;
        }
        double determinant = xx * ww - xw * xw;
        double outdoor = determinant > 1e-9 ? (xy * ww - wy * xw) / determinant : xy / Math.max(xx, 1e-9);
        double wind = determinant > 1e-9 ? (wy * xx - xy * xw) / determinant : 0;
        outdoor = clamp(outdoor, 0.001, 0.100);
        wind = clamp(wind, 0.0, 0.050);
        double squaredError = 0;
        for (CoolingSample sample : samples) {
            double predicted = outdoor * sample.outdoorDelta().doubleValue() + wind * sample.wind().doubleValue();
            double error = sample.coolingPerHour().doubleValue() - predicted;
            squaredError += error * error;
        }
        double rmse = Math.sqrt(squaredError / samples.size());
        double confidence = Math.min(1.0, samples.size() / 96.0) * (1.0 / (1.0 + rmse));
        return new Estimate(decimal(outdoor), decimal(wind), samples.size(), decimal(confidence));
    }

    private void updateMetadata(HeatingPlannerRoomEntity room, Estimate estimate, boolean learned, Instant now) {
        room.setModelParametersLearned(learned);
        room.setModelSampleCount(estimate.sampleCount());
        room.setModelConfidence(learned ? estimate.confidence() : BigDecimal.ZERO);
        room.setModelTrainedAt(now);
        roomRepository.save(room);
    }

    private SiteWeatherEntity nearestWeather(List<SiteWeatherEntity> weather, Instant time) {
        SiteWeatherEntity nearest = weather.stream().min(Comparator.comparing(point ->
                Duration.between(point.getForecastTime(), time).abs())).orElse(null);
        return nearest != null && Duration.between(nearest.getForecastTime(), time).abs().compareTo(MAX_WEATHER_GAP) <= 0
                ? nearest : null;
    }

    private Instant midpoint(Instant first, Instant second) {
        return first.plusMillis(Duration.between(first, second).toMillis() / 2);
    }

    private BigDecimal blend(BigDecimal configured, BigDecimal learned, BigDecimal confidence) {
        return configured.multiply(BigDecimal.ONE.subtract(confidence)).add(learned.multiply(confidence))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    public record ModelResolution(HeatingPlanSimulationService.ThermalModel model, boolean learned,
                                  int sampleCount, BigDecimal confidence, String reason) {
        static ModelResolution configured(HeatingPlanSimulationService.ThermalModel model, String reason) {
            return new ModelResolution(model, false, 0, BigDecimal.ZERO, reason);
        }
    }

    record Estimate(BigDecimal outdoorLossRate, BigDecimal windLossRate, int sampleCount, BigDecimal confidence) { }
    private record CoolingSample(BigDecimal outdoorDelta, BigDecimal wind, BigDecimal coolingPerHour) { }
}
