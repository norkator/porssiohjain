package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.WaterMeterEntity;
import com.nitramite.porssiohjain.entity.WaterMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import com.nitramite.porssiohjain.entity.repository.WaterMeterRepository;
import com.nitramite.porssiohjain.entity.repository.WaterMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** A separate read-only widget source; existing device measurement contracts remain unchanged. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaterMeterWidgetQueryService {
    public enum Type { WATER_METER_TOTAL, WATER_CONSUMPTION }
    private static final ZoneId ZONE = ZoneId.of("Europe/Helsinki");
    private final WaterMeterRepository meters;
    private final WaterMeasurementRepository measurements;

    public List<Reading> latest(Long accountId) {
        return latest(accountId, Instant.now());
    }

    List<Reading> latest(Long accountId, Instant now) {
        requireAccount(accountId);
        List<Reading> result = new ArrayList<>();
        for (var meter : meters.findByAccountIdOrderByNameAscIdAsc(accountId)) {
            var estimate = WaterMeterEstimation.estimate(meter, accountId, measurements, now);
            if (meter.getLatestTotalCubicMeters() != null && meter.getObservedAt() != null)
                result.add(total(meter, estimate));
            measurements.findFirstByWaterMeterIdAndWaterMeterAccountIdAndConsumptionDateIsNotNullAndConsumptionCubicMetersIsNotNullOrderByConsumptionDateDescIdDesc(
                    meter.getId(), accountId).ifPresent(row -> result.add(daily(meter, row, estimate)));
        }
        return result;
    }

    public History history(Long accountId, Long meterId, Type type, String key, int hours, Instant now) {
        requireAccount(accountId);
        if (meterId == null || meterId <= 0 || type == null || !key(type).equals(key)
                || (hours != 24 && hours != 48 && hours != 168))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid water measurement or history period");
        var meter = meters.findByIdAndAccountId(meterId, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Water measurement unavailable"));
        Instant from = now.minus(Duration.ofHours(hours));
        var rows = measurements.findWidgetHistory(meterId, accountId, from, now,
                from.atZone(ZONE).toLocalDate().minusDays(1), now.atZone(ZONE).toLocalDate().plusDays(1));
        List<Point> points = rows.stream().filter(row -> value(row, type) != null)
                .map(row -> new Point(pointTime(row), value(row, type), row.getConsumptionDate()))
                .filter(point -> !point.observedAt().isBefore(from) && point.observedAt().isBefore(now))
                .sorted(Comparator.comparing(Point::observedAt)).toList();
        var estimate = WaterMeterEstimation.estimate(meter, accountId, measurements, now);
        Reading latest = type == Type.WATER_METER_TOTAL
                ? (meter.getLatestTotalCubicMeters() == null || meter.getObservedAt() == null ? null : total(meter, estimate))
                : measurements.findFirstByWaterMeterIdAndWaterMeterAccountIdAndConsumptionDateIsNotNullAndConsumptionCubicMetersIsNotNullOrderByConsumptionDateDescIdDesc(
                        meterId, accountId).map(row -> daily(meter, row, estimate)).orElse(null);
        return new History(meterId, type, key, from, now, latest, points, projectedPoints(estimate, type, from));
    }

    private static List<Point> projectedPoints(WaterMeterEstimation.Result estimate, Type type, Instant from) {
        if (estimate == null) return List.of();
        var source = type == Type.WATER_METER_TOTAL ? estimate.totalPoints() : estimate.dailyPoints();
        List<Point> points = new ArrayList<>();
        WaterMeterEstimation.Point previous = null;
        for (var point : source) {
            if (!point.at().isBefore(from)) {
                if (points.isEmpty() && previous != null && point.at().isAfter(from)) {
                    var fraction = BigDecimal.valueOf(Duration.between(previous.at(), from).toMillis())
                            .divide(BigDecimal.valueOf(Duration.between(previous.at(), point.at()).toMillis()),
                                    12, java.math.RoundingMode.HALF_UP);
                    points.add(new Point(from, previous.value().add(point.value().subtract(previous.value()).multiply(fraction)),
                            point.consumptionDate()));
                }
                points.add(new Point(point.at(), point.value(), point.consumptionDate()));
            }
            previous = point;
        }
        return points;
    }

    private static Instant pointTime(WaterMeasurementEntity row) {
        return row.getConsumptionDate() == null ? row.getObservedAt()
                : row.getConsumptionDate().plusDays(1).atStartOfDay(ZONE).toInstant();
    }
    private static BigDecimal value(WaterMeasurementEntity row, Type type) {
        return type == Type.WATER_METER_TOTAL ? row.getTotalCubicMeters() : row.getConsumptionCubicMeters();
    }
    private static String key(Type type) { return type == Type.WATER_METER_TOTAL ? "total_m3" : "daily_m3"; }
    private static Reading total(WaterMeterEntity meter, WaterMeterEstimation.Result estimate) {
        return reading(meter, Type.WATER_METER_TOTAL, meter.getLatestTotalCubicMeters(), meter.getObservedAt(),
                meter.getConsumptionThrough(), estimate == null ? null : estimate.total());
    }
    private static Reading daily(WaterMeterEntity meter, WaterMeasurementEntity row, WaterMeterEstimation.Result estimate) {
        return reading(meter, Type.WATER_CONSUMPTION, row.getConsumptionCubicMeters(), row.getObservedAt(), row.getConsumptionDate(),
                estimate == null ? null : estimate.daily());
    }
    private static Reading reading(WaterMeterEntity meter, Type type, BigDecimal value, Instant observed, LocalDate date,
                                   WaterMeterEstimation.Estimate estimate) {
        return new Reading(meter.getId(), meter.getName(), type, key(type), "WATER_METER", meter.getProvider().name(),
                "m³", value, observed, null, observed,
                type == Type.WATER_METER_TOTAL && meter.getProvider() == WaterMeterProvider.TAMPEREEN_VESI, date, estimate);
    }
    private static void requireAccount(Long id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    // deviceId is the water-meter ID in this separate namespace. Type/key distinguish it in widget selections.
    public record Reading(Long deviceId, String deviceName, Type measurementType, String measurementKey, String source,
                          String sourceKey, String unit, BigDecimal value, Instant observedAt, Instant measuredAt,
                          Instant receivedAt, boolean calculated, LocalDate consumptionDate, WaterMeterEstimation.Estimate estimate) { }
    public record Point(Instant observedAt, BigDecimal value, LocalDate consumptionDate) { }
    public record History(Long deviceId, Type measurementType, String measurementKey, Instant from, Instant to,
                          Reading latest, List<Point> points, List<Point> estimatedPoints) { }
}
