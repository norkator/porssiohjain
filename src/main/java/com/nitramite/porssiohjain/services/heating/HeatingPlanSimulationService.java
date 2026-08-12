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

package com.nitramite.porssiohjain.services.heating;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class HeatingPlanSimulationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal PRICE_THRESHOLD_MINIMUM_GAP = new BigDecimal("0.0001");

    /**
     * Uses the lower and upper quartiles of the complete planning horizon. This adapts cheap and expensive
     * classification to the site's actual tax-and-transfer-inclusive prices instead of fixed cent values.
     */
    public PriceThresholds calculateDynamicPriceThresholds(List<MarketPoint> market) {
        return calculateDynamicPriceThresholds(market, new BigDecimal("0.25"), new BigDecimal("0.75"));
    }

    public PriceThresholds calculateDynamicPriceThresholds(List<MarketPoint> market,
                                                           BigDecimal cheapPricePercentile,
                                                           BigDecimal expensivePricePercentile) {
        if (market == null || market.isEmpty()) {
            throw new IllegalArgumentException("Market points are required to calculate dynamic price thresholds");
        }
        if (cheapPricePercentile == null || expensivePricePercentile == null
                || cheapPricePercentile.compareTo(ZERO) < 0
                || expensivePricePercentile.compareTo(BigDecimal.ONE) > 0
                || cheapPricePercentile.compareTo(expensivePricePercentile) >= 0) {
            throw new IllegalArgumentException("Price percentiles must be between 0 and 1, and cheap must be below expensive");
        }
        List<BigDecimal> prices = market.stream()
                .map(MarketPoint::priceCentsPerKwh)
                .sorted()
                .toList();
        BigDecimal cheap = percentile(prices, cheapPricePercentile);
        BigDecimal expensive = percentile(prices, expensivePricePercentile);
        if (expensive.compareTo(cheap) <= 0) {
            expensive = cheap.add(PRICE_THRESHOLD_MINIMUM_GAP);
        }
        return new PriceThresholds(cheap, expensive);
    }

    private BigDecimal percentile(List<BigDecimal> sorted, BigDecimal percentile) {
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }
        BigDecimal position = BigDecimal.valueOf(sorted.size() - 1).multiply(percentile);
        int lowerIndex = position.setScale(0, RoundingMode.FLOOR).intValueExact();
        int upperIndex = position.setScale(0, RoundingMode.CEILING).intValueExact();
        BigDecimal lower = sorted.get(lowerIndex);
        if (lowerIndex == upperIndex) {
            return lower;
        }
        BigDecimal fraction = position.subtract(BigDecimal.valueOf(lowerIndex));
        return lower.add(sorted.get(upperIndex).subtract(lower).multiply(fraction))
                .setScale(4, RoundingMode.HALF_UP);
    }

    public SimulationResult simulate(SimulationRequest request) {
        validate(request);
        List<MarketPoint> market = request.market().stream()
                .sorted(Comparator.comparing(MarketPoint::time))
                .toList();
        boolean plannerActive = market.stream()
                .map(MarketPoint::outdoorTemperature)
                .anyMatch(temperature -> temperature.compareTo(
                        request.settings().plannerActivationOutdoorTemperature()) < 0);
        Duration step = request.settings().step();
        BigDecimal stepHours = BigDecimal.valueOf(step.toMinutes())
                .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
        BigDecimal floorTemperature = request.initialFloorTemperature();
        BigDecimal roomTemperature = request.initialRoomTemperature();
        List<SimulationPoint> points = new ArrayList<>();
        BigDecimal energyKwh = ZERO;
        BigDecimal energyCost = ZERO;
        WoodStoveRecommendation woodRecommendation = plannerActive ? planWoodStove(request, market) : null;
        Set<Instant> preheatTimes = plannerActive ? planPreheatTimes(request, market) : Set.of();

        for (MarketPoint point : market) {
            OperatingDecision decision = decide(point, roomTemperature, request.settings(), woodRecommendation,
                    plannerActive, preheatTimes, request.roomMeasurementFresh());
            boolean heating = floorTemperature.compareTo(decision.floorSetpoint()) < 0;
            BigDecimal floorToRoom = request.model().floorToRoomRate()
                    .multiply(floorTemperature.subtract(roomTemperature));
            BigDecimal heaterGain = heating ? request.model().floorHeatingRate() : ZERO;
            BigDecimal outdoorLoss = request.model().roomOutdoorLossRate()
                    .multiply(roomTemperature.subtract(point.outdoorTemperature()).max(ZERO));
            BigDecimal windLoss = request.model().windLossRate()
                    .multiply(point.windSpeedMs().max(ZERO));
            BigDecimal woodHeatRate = woodHeatRateAt(point.time(), woodRecommendation);

            floorTemperature = floorTemperature
                    .add(heaterGain.subtract(floorToRoom).multiply(stepHours))
                    .min(request.settings().absoluteMaximumFloorTemperature());
            roomTemperature = roomTemperature
                    .add(floorToRoom.add(woodHeatRate).subtract(outdoorLoss).subtract(windLoss).multiply(stepHours));

            BigDecimal stepEnergy = heating
                    ? request.model().heaterPowerKw().multiply(stepHours)
                    : ZERO;
            energyKwh = energyKwh.add(stepEnergy);
            energyCost = energyCost.add(stepEnergy.multiply(point.priceCentsPerKwh()));

            points.add(new SimulationPoint(
                    point.time(), point.priceCentsPerKwh(), point.outdoorTemperature(),
                    floorTemperature.setScale(2, RoundingMode.HALF_UP),
                    roomTemperature.setScale(2, RoundingMode.HALF_UP),
                    decision.floorSetpoint(), woodHeatRate.setScale(3, RoundingMode.HALF_UP),
                    heating, decision.mode(), decision.reason()
            ));
        }

        return new SimulationResult(
                List.copyOf(points),
                energyKwh.setScale(3, RoundingMode.HALF_UP),
                energyCost.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP),
                woodRecommendation,
                plannerActive,
                plannerActive ? "Forecast is below the configured planner activation temperature"
                        : "Heating optimization is inactive because the forecast stays above the configured activation temperature"
        );
    }

    /**
     * Plans charging over the complete supplied horizon. The amount of charging is derived from forecast heat loss
     * during each expensive block and the configured floor response; it is deliberately not bounded by an arbitrary
     * look-ahead duration. Cheapest eligible points are preferred, with later points winning equal-price ties so less
     * stored heat is lost before it is needed.
     */
    private Set<Instant> planPreheatTimes(SimulationRequest request, List<MarketPoint> market) {
        if (!request.floorMeasurementFresh()) {
            return Set.of();
        }
        Settings settings = request.settings();
        ThermalModel model = request.model();
        BigDecimal stepHours = BigDecimal.valueOf(settings.step().toMinutes())
                .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
        BigDecimal usableFloorRise = settings.maximumPreheatFloorTemperature()
                .subtract(request.initialFloorTemperature()).max(ZERO);
        BigDecimal gainPerStep = model.floorHeatingRate().multiply(stepHours);
        if (usableFloorRise.signum() <= 0 || gainPerStep.signum() <= 0) {
            return Set.of();
        }

        Set<Instant> selected = new HashSet<>();
        int index = 0;
        while (index < market.size()) {
            if (!isExpensive(market.get(index), settings)) {
                index++;
                continue;
            }
            int blockStart = index;
            BigDecimal forecastLoss = ZERO;
            while (index < market.size() && isExpensive(market.get(index), settings)) {
                MarketPoint point = market.get(index++);
                BigDecimal outdoorLoss = model.roomOutdoorLossRate()
                        .multiply(settings.minimumRoomTemperature().subtract(point.outdoorTemperature()).max(ZERO));
                BigDecimal windLoss = model.windLossRate().multiply(point.windSpeedMs().max(ZERO));
                forecastLoss = forecastLoss.add(outdoorLoss.add(windLoss).multiply(stepHours));
            }

            // Only the reserve that the floor can safely hold is useful. Convert that reserve to heater-on steps.
            BigDecimal requiredFloorRise = forecastLoss
                    .divide(model.floorToRoomRate().max(new BigDecimal("0.000001")), 8, RoundingMode.HALF_UP)
                    .min(usableFloorRise);
            int requiredSteps = requiredFloorRise.divide(gainPerStep, 0, RoundingMode.CEILING).intValue();
            List<MarketPoint> candidates = market.subList(0, blockStart).stream()
                    .filter(candidate -> candidate.priceCentsPerKwh().compareTo(settings.cheapPriceThreshold()) <= 0)
                    .filter(candidate -> !selected.contains(candidate.time()))
                    .sorted(Comparator.comparing(MarketPoint::priceCentsPerKwh)
                            .thenComparing(MarketPoint::time, Comparator.reverseOrder()))
                    .limit(requiredSteps)
                    .toList();
            candidates.forEach(candidate -> selected.add(candidate.time()));
        }
        return Set.copyOf(selected);
    }

    private boolean isExpensive(MarketPoint point, Settings settings) {
        return point.priceCentsPerKwh().compareTo(settings.expensivePriceThreshold()) >= 0;
    }

    private WoodStoveRecommendation planWoodStove(SimulationRequest request, List<MarketPoint> market) {
        WoodStoveSettings stove = request.woodStove();
        if (stove == null || !stove.enabled() || !stove.loaded()) {
            return null;
        }
        MarketPoint expensiveStart = market.stream()
                .filter(point -> point.priceCentsPerKwh()
                        .compareTo(request.settings().expensivePriceThreshold()) >= 0)
                .filter(point -> point.outdoorTemperature()
                        .compareTo(stove.woodRecommendationOutdoorTemperature()) < 0)
                .findFirst()
                .orElse(null);
        if (expensiveStart == null
                || request.initialRoomTemperature().compareTo(request.settings().maximumRoomTemperature()) >= 0) {
            return null;
        }
        Instant releaseStartsAt = expensiveStart.time();
        Instant notifyAt = releaseStartsAt.minus(stove.releaseDelay());
        boolean userAvailable = stove.availability().stream()
                .anyMatch(window -> !notifyAt.isBefore(window.from()) && !notifyAt.isAfter(window.to()));
        if (!userAvailable) {
            return null;
        }
        return new WoodStoveRecommendation(
                stove.loadName(), stove.woodAmount(), notifyAt, releaseStartsAt,
                releaseStartsAt.plus(stove.releaseDuration()), stove.initialRoomHeatingRate(),
                "Light the configured wood load so useful heat begins when the expensive period starts"
        );
    }

    private BigDecimal woodHeatRateAt(Instant time, WoodStoveRecommendation recommendation) {
        if (recommendation == null || time.isBefore(recommendation.releaseStartsAt())
                || !time.isBefore(recommendation.releaseEndsAt())) {
            return ZERO;
        }
        long totalSeconds = Duration.between(recommendation.releaseStartsAt(), recommendation.releaseEndsAt()).toSeconds();
        long remainingSeconds = Duration.between(time, recommendation.releaseEndsAt()).toSeconds();
        return recommendation.initialRoomHeatingRate()
                .multiply(BigDecimal.valueOf(remainingSeconds))
                .divide(BigDecimal.valueOf(totalSeconds), 8, RoundingMode.HALF_UP);
    }

    private OperatingDecision decide(MarketPoint current, BigDecimal roomTemperature, Settings settings,
                                     WoodStoveRecommendation woodRecommendation, boolean plannerActive,
                                     Set<Instant> preheatTimes, boolean roomMeasurementFresh) {
        if (!plannerActive) {
            return new OperatingDecision(settings.normalFloorTemperature(), OperatingMode.INACTIVE,
                    "Forecast stays above the configured Heating Planner activation temperature");
        }
        if (!roomMeasurementFresh) {
            return new OperatingDecision(settings.normalFloorTemperature(), OperatingMode.INACTIVE,
                    "Room measurement is missing or stale; keep optimization inactive and use the existing controller fallback");
        }
        if (roomTemperature.compareTo(settings.maximumRoomTemperature()) >= 0) {
            return new OperatingDecision(settings.dischargeFloorSetpoint(), OperatingMode.DISCHARGE,
                    "Room temperature is at the configured comfort maximum; suppress electric heating");
        }
        if (roomTemperature.compareTo(settings.minimumRoomTemperature()) < 0) {
            return new OperatingDecision(settings.normalFloorTemperature(), OperatingMode.COMFORT_RECOVERY,
                    "Room temperature is below the configured comfort minimum");
        }
        if (woodHeatRateAt(current.time(), woodRecommendation).signum() > 0) {
            return new OperatingDecision(settings.dischargeFloorSetpoint(), OperatingMode.DISCHARGE,
                    "The wood stove is predicted to release heat; suppress electric floor heating");
        }
        if (current.priceCentsPerKwh().compareTo(settings.expensivePriceThreshold()) >= 0) {
            return new OperatingDecision(settings.dischargeFloorSetpoint(), OperatingMode.DISCHARGE,
                    "Electricity price is in the expensive range; use stored floor heat");
        }
        if (preheatTimes.contains(current.time())) {
            return new OperatingDecision(settings.maximumPreheatFloorTemperature(), OperatingMode.PREHEAT,
                    "Selected from the full forecast horizon to store enough cheap heat for a later expensive period");
        }
        return new OperatingDecision(settings.normalFloorTemperature(), OperatingMode.NORMAL,
                "Maintain the normal floor temperature");
    }

    private void validate(SimulationRequest request) {
        if (request == null || request.settings() == null || request.model() == null
                || request.market() == null || request.market().isEmpty()) {
            throw new IllegalArgumentException("Simulation request, settings, model, and market points are required");
        }
        Settings settings = request.settings();
        if (settings.step().isZero() || settings.step().isNegative()) {
            throw new IllegalArgumentException("Simulation step must be positive");
        }
        if (settings.maximumPreheatFloorTemperature()
                .compareTo(settings.absoluteMaximumFloorTemperature()) > 0) {
            throw new IllegalArgumentException("Maximum preheat temperature cannot exceed the absolute floor maximum");
        }
        if (settings.normalFloorTemperature().compareTo(settings.maximumPreheatFloorTemperature()) > 0) {
            throw new IllegalArgumentException("Normal floor temperature cannot exceed the preheat maximum");
        }
        if (settings.minimumRoomTemperature().compareTo(settings.maximumRoomTemperature()) >= 0) {
            throw new IllegalArgumentException("Minimum room temperature must be below the maximum room temperature");
        }
        if (request.woodStove() != null && request.woodStove().enabled()
                && (request.woodStove().releaseDelay().isNegative()
                || request.woodStove().releaseDuration().isZero()
                || request.woodStove().releaseDuration().isNegative()
                || request.woodStove().initialRoomHeatingRate().signum() < 0)) {
            throw new IllegalArgumentException("Wood-stove delay, duration, and heating rate must be valid");
        }
        if (request.woodStove() != null && request.woodStove().availability() == null) {
            throw new IllegalArgumentException("Wood-stove availability is required");
        }
        if (request.model().heaterPowerKw().signum() < 0
                || request.model().floorHeatingRate().signum() < 0
                || request.model().floorToRoomRate().signum() < 0
                || request.model().roomOutdoorLossRate().signum() < 0
                || request.model().windLossRate().signum() < 0) {
            throw new IllegalArgumentException("Thermal model rates cannot be negative");
        }
    }

    public record SimulationRequest(
            BigDecimal initialFloorTemperature,
            BigDecimal initialRoomTemperature,
            Settings settings,
            ThermalModel model,
            List<MarketPoint> market,
            WoodStoveSettings woodStove,
            boolean floorMeasurementFresh,
            boolean roomMeasurementFresh
    ) {
    }

    public record Settings(
            Duration step,
            BigDecimal cheapPriceThreshold,
            BigDecimal expensivePriceThreshold,
            BigDecimal normalFloorTemperature,
            BigDecimal maximumPreheatFloorTemperature,
            BigDecimal absoluteMaximumFloorTemperature,
            BigDecimal dischargeFloorSetpoint,
            BigDecimal minimumRoomTemperature,
            BigDecimal maximumRoomTemperature,
            BigDecimal plannerActivationOutdoorTemperature
    ) {
    }

    public record ThermalModel(
            BigDecimal heaterPowerKw,
            BigDecimal floorHeatingRate,
            BigDecimal floorToRoomRate,
            BigDecimal roomOutdoorLossRate,
            BigDecimal windLossRate
    ) {
    }

    public record MarketPoint(
            Instant time,
            BigDecimal priceCentsPerKwh,
            BigDecimal outdoorTemperature,
            BigDecimal windSpeedMs
    ) {
    }

    public record PriceThresholds(BigDecimal cheapPriceThreshold, BigDecimal expensivePriceThreshold) {
    }

    public record WoodStoveSettings(
            boolean enabled,
            boolean loaded,
            String loadName,
            BigDecimal woodAmount,
            Duration releaseDelay,
            Duration releaseDuration,
            BigDecimal initialRoomHeatingRate,
            BigDecimal woodRecommendationOutdoorTemperature,
            List<StoveAvailability> availability
    ) {
    }

    public record StoveAvailability(
            Instant from,
            Instant to
    ) {
    }

    public record SimulationPoint(
            Instant time,
            BigDecimal priceCentsPerKwh,
            BigDecimal outdoorTemperature,
            BigDecimal floorTemperature,
            BigDecimal roomTemperature,
            BigDecimal floorSetpoint,
            BigDecimal woodRoomHeatingRate,
            boolean heating,
            OperatingMode mode,
            String reason
    ) {
    }

    public record SimulationResult(
            List<SimulationPoint> points,
            BigDecimal energyKwh,
            BigDecimal estimatedCostEur,
            WoodStoveRecommendation woodStoveRecommendation,
            boolean plannerActive,
            String plannerStatusReason
    ) {
    }

    public record WoodStoveRecommendation(
            String loadName,
            BigDecimal woodAmount,
            Instant notifyAt,
            Instant releaseStartsAt,
            Instant releaseEndsAt,
            BigDecimal initialRoomHeatingRate,
            String reason
    ) {
    }

    private record OperatingDecision(BigDecimal floorSetpoint, OperatingMode mode, String reason) {
    }

    public enum OperatingMode {
        NORMAL,
        INACTIVE,
        PREHEAT,
        DISCHARGE,
        COMFORT_RECOVERY
    }
}
