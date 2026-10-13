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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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

    private static final Duration TRAINING_WINDOW = Duration.ofDays(14);
    static final Duration MAX_MODEL_EVIDENCE_AGE = Duration.ofDays(14);
    static final BigDecimal MINIMUM_MODEL_CONFIDENCE = new BigDecimal("0.2500");
    private static final Duration SAMPLE_INTERVAL = Duration.ofMinutes(15);
    private static final Duration MAX_SAMPLE_GAP = Duration.ofHours(2);
    private static final Duration MAX_WEATHER_GAP = Duration.ofMinutes(90);
    private static final int MIN_SAMPLES = 24;
    private static final int PAGE_SIZE = 1000;

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
        return learnAndResolve(room, configured, now);
    }

    @Transactional(readOnly = true)
    public List<ModelStatus> modelStatuses(Long accountId, Long siteId, Instant now) {
        return roomRepository.findByAccountIdAndSiteIdOrderBySortOrderAscIdAsc(accountId, siteId).stream()
                .map(room -> modelStatus(room, now)).toList();
    }

    @Transactional
    public List<ModelRebuildResult> rebuildModels(Long accountId, Long siteId, Instant now) {
        return roomRepository.findByAccountIdAndSiteIdOrderBySortOrderAscIdAsc(accountId, siteId).stream()
                .map(room -> {
                    var resolution = room.getRoomSensorDevice() == null
                            ? ModelResolution.configured(configuredModel(room), "No saved room sensor is available for learning")
                            : learnAndResolve(room, configuredModel(room), now);
                    return new ModelRebuildResult(modelStatus(room, now), resolution.reason());
                }).toList();
    }

    private ModelResolution learnAndResolve(HeatingPlannerRoomEntity room,
                                           HeatingPlanSimulationService.ThermalModel configured, Instant now) {
        String key = room.getRoomSensorMeasurementKey();
        if (key == null || key.isBlank()) key = HeatingPlannerMeasurementService.DEFAULT_TEMPERATURE_KEY;
        Instant from = now.minus(TRAINING_WINDOW);
        List<SiteWeatherEntity> weather = weatherRepository
                .findBySiteAndForecastTimeBetweenOrderByForecastTimeAsc(room.getSite(), from, now);
        // Start with measurements that can still be matched to retained weather.
        Instant weatherFrom = weather.stream().filter(point -> point.getTemperature() != null)
                .map(SiteWeatherEntity::getForecastTime).min(Comparator.naturalOrder()).orElse(null);
        if (weatherFrom != null && weatherFrom.minus(MAX_WEATHER_GAP).isAfter(from))
            from = weatherFrom.minus(MAX_WEATHER_GAP);
        List<ZigbeeDeviceMeasurementEntity> temperatures = weatherFrom == null ? List.of()
                : trainingTemperatures(room.getRoomSensorDevice().getId(), key, from, now);
        Estimate estimate = estimate(temperatures, weather);
        boolean learned = estimate.sampleCount() >= MIN_SAMPLES;
        String candidateReason = weatherFrom == null ? "No retained weather is available to match room measurements"
                : "Latest history has " + estimate.sampleCount() + " trustworthy cooling intervals"
                + (learned ? " with " + percent(estimate.confidence()) + " confidence"
                : "; at least " + MIN_SAMPLES + " are needed. Flat and warming readings do not train the cooling model");
        if ((!learned || estimate.confidence().compareTo(MINIMUM_MODEL_CONFIDENCE) < 0)
                && retainedModelUsable(room, now)) {
            return resolve(configured, room.getRoomOutdoorLossRate(), room.getWindLossRate(),
                    room.getModelSampleCount(), room.getModelConfidence(),
                    "Retained learned cooling model; " + candidateReason
                            + ". Evidence remains valid until " + room.getModelEvidenceAt().plus(MAX_MODEL_EVIDENCE_AGE));
        }
        if (learned) {
            room.setRoomOutdoorLossRate(estimate.outdoorLossRate());
            room.setWindLossRate(estimate.windLossRate());
        }
        updateMetadata(room, estimate, learned, now);
        return learned ? resolve(configured, estimate.outdoorLossRate(), estimate.windLossRate(),
                estimate.sampleCount(), estimate.confidence(), "Rebuilt observed cooling model; " + candidateReason)
                : new ModelResolution(configured, false, estimate.sampleCount(), BigDecimal.ZERO,
                "Configured model: " + candidateReason);
    }

    private List<ZigbeeDeviceMeasurementEntity> trainingTemperatures(Long deviceId, String key, Instant from, Instant now) {
        List<ZigbeeDeviceMeasurementEntity> sampled = new ArrayList<>();
        Pageable page = PageRequest.of(0, PAGE_SIZE);
        Slice<ZigbeeDeviceMeasurementEntity> rows;
        do {
            rows = measurementRepository
                    .findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
                            deviceId, ZigbeeMeasurementType.TEMPERATURE, key, from, now, page);
            rows.forEach(row -> appendSample(sampled, row));
            page = rows.nextPageable();
        } while (rows.hasNext());
        return sampled;
    }

    private void appendSample(List<ZigbeeDeviceMeasurementEntity> sampled, ZigbeeDeviceMeasurementEntity row) {
        if (sampled.isEmpty() || !row.getMeasuredAt().isBefore(
                sampled.getLast().getMeasuredAt().plus(SAMPLE_INTERVAL))) sampled.add(row);
    }

    Estimate estimate(List<ZigbeeDeviceMeasurementEntity> temperatures, List<SiteWeatherEntity> weather) {
        List<ZigbeeDeviceMeasurementEntity> sampled = new ArrayList<>();
        temperatures.forEach(row -> appendSample(sampled, row));
        List<CoolingSample> samples = new ArrayList<>();
        Instant evidenceAt = null;
        for (int i = 1; i < sampled.size(); i++) {
            ZigbeeDeviceMeasurementEntity before = sampled.get(i - 1);
            ZigbeeDeviceMeasurementEntity after = sampled.get(i);
            Duration elapsed = Duration.between(before.getMeasuredAt(), after.getMeasuredAt());
            if (elapsed.compareTo(MAX_SAMPLE_GAP) > 0) continue;
            BigDecimal change = after.getValue().subtract(before.getValue());
            if (change.signum() >= 0 || change.abs().compareTo(new BigDecimal("3.0")) > 0) continue;
            SiteWeatherEntity nearest = nearestWeather(weather, midpoint(before.getMeasuredAt(), after.getMeasuredAt()));
            if (nearest == null || nearest.getTemperature() == null) continue;
            BigDecimal hours = BigDecimal.valueOf(elapsed.toMillis()).divide(BigDecimal.valueOf(3_600_000), 8, RoundingMode.HALF_UP);
            BigDecimal coolingPerHour = change.negate().divide(hours, 8, RoundingMode.HALF_UP);
            BigDecimal averageRoom = before.getValue().add(after.getValue()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            BigDecimal deltaOutdoor = averageRoom.subtract(nearest.getTemperature()).max(BigDecimal.ZERO);
            BigDecimal wind = nearest.getWindSpeedMs() == null ? BigDecimal.ZERO : nearest.getWindSpeedMs().max(BigDecimal.ZERO);
            if (deltaOutdoor.signum() > 0) {
                samples.add(new CoolingSample(deltaOutdoor, wind, coolingPerHour));
                evidenceAt = after.getMeasuredAt();
            }
        }
        if (samples.isEmpty()) return new Estimate(BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, null);

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
        return new Estimate(decimal(outdoor), decimal(wind), samples.size(),
                BigDecimal.valueOf(confidence).setScale(4, RoundingMode.DOWN), evidenceAt);
    }

    private void updateMetadata(HeatingPlannerRoomEntity room, Estimate estimate, boolean learned, Instant now) {
        room.setModelParametersLearned(learned);
        room.setModelSampleCount(estimate.sampleCount());
        room.setModelConfidence(learned ? estimate.confidence() : BigDecimal.ZERO);
        room.setModelTrainedAt(learned ? now : null);
        room.setModelEvidenceAt(learned ? estimate.evidenceAt() : null);
        roomRepository.save(room);
    }

    static boolean hasCurrentModelEvidence(HeatingPlannerRoomEntity room, Instant now) {
        return room.getModelEvidenceAt() != null && !room.getModelEvidenceAt().isAfter(now)
                && !room.getModelEvidenceAt().isBefore(now.minus(MAX_MODEL_EVIDENCE_AGE));
    }

    static boolean passesActiveModelChecks(HeatingPlannerRoomEntity room, Instant now) {
        return room.isModelParametersLearned() && room.getModelConfidence() != null
                && room.getModelConfidence().compareTo(MINIMUM_MODEL_CONFIDENCE) >= 0
                && room.getModelConfidence().compareTo(BigDecimal.ONE) <= 0
                && hasCurrentModelEvidence(room, now);
    }

    private boolean retainedModelUsable(HeatingPlannerRoomEntity room, Instant now) {
        return passesActiveModelChecks(room, now)
                && room.getModelSampleCount() != null && room.getModelSampleCount() >= MIN_SAMPLES
                && within(room.getRoomOutdoorLossRate(), "0.001", "0.100")
                && within(room.getWindLossRate(), "0.0", "0.050");
    }

    private boolean within(BigDecimal value, String minimum, String maximum) {
        return value != null && value.compareTo(new BigDecimal(minimum)) >= 0
                && value.compareTo(new BigDecimal(maximum)) <= 0;
    }

    private ModelStatus modelStatus(HeatingPlannerRoomEntity room, Instant now) {
        BigDecimal confidence = room.getModelConfidence() == null ? BigDecimal.ZERO : room.getModelConfidence();
        String status = !room.isModelParametersLearned() ? "Needs cooling observations"
                : !hasCurrentModelEvidence(room, now) ? "Evidence missing or older than 14 days"
                : confidence.compareTo(MINIMUM_MODEL_CONFIDENCE) < 0 ? "Below the 25% model requirement"
                : "Meets the 25% model requirement";
        return new ModelStatus(room.getId(), room.getName(), room.isEnabled(), confidence,
                room.getModelSampleCount() == null ? 0 : room.getModelSampleCount(), room.getModelTrainedAt(),
                room.getModelEvidenceAt(), room.getRoomOutdoorLossRate(), room.getWindLossRate(), status);
    }

    private HeatingPlanSimulationService.ThermalModel configuredModel(HeatingPlannerRoomEntity room) {
        return new HeatingPlanSimulationService.ThermalModel(value(room.getHeaterPowerKw(), "2.0"),
                value(room.getFloorHeatingRate(), "0.8"), value(room.getFloorToRoomRate(), "0.06"),
                value(room.getRoomOutdoorLossRate(), "0.012"), value(room.getWindLossRate(), "0.001"));
    }

    private BigDecimal value(BigDecimal value, String fallback) {
        return value == null ? new BigDecimal(fallback) : value;
    }

    private ModelResolution resolve(HeatingPlanSimulationService.ThermalModel configured, BigDecimal outdoor,
                                    BigDecimal wind, int samples, BigDecimal confidence, String reason) {
        var model = new HeatingPlanSimulationService.ThermalModel(configured.heaterPowerKw(),
                configured.floorHeatingRate(), configured.floorToRoomRate(),
                blend(configured.roomOutdoorLossRate(), outdoor, confidence),
                blend(configured.windLossRate(), wind, confidence));
        return new ModelResolution(model, true, samples, confidence, reason);
    }

    private String percent(BigDecimal confidence) {
        return confidence.movePointRight(2).setScale(2, RoundingMode.HALF_UP) + "%";
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

    public record ModelStatus(Long roomId, String roomName, boolean enabled, BigDecimal confidence, int sampleCount,
                              Instant trainedAt, Instant evidenceAt, BigDecimal outdoorLossRate, BigDecimal windLossRate,
                              String status) { }

    public record ModelRebuildResult(ModelStatus status, String reason) { }

    record Estimate(BigDecimal outdoorLossRate, BigDecimal windLossRate, int sampleCount, BigDecimal confidence,
                    Instant evidenceAt) { }
    private record CoolingSample(BigDecimal outdoorDelta, BigDecimal wind, BigDecimal coolingPerHour) { }
}
