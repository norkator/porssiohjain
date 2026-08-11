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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeatingPlanSimulationServiceTest {

    private final HeatingPlanSimulationService service = new HeatingPlanSimulationService();

    @Test
    void calculatesPriceLimitsFromLowerAndUpperQuartiles() {
        Instant start = Instant.parse("2026-01-15T00:00:00Z");
        var market = List.of(
                point(start, "1.0"), point(start.plusSeconds(1), "3.0"),
                point(start.plusSeconds(2), "7.0"), point(start.plusSeconds(3), "11.0"),
                point(start.plusSeconds(4), "18.0"), point(start.plusSeconds(5), "25.0"),
                point(start.plusSeconds(6), "30.0"), point(start.plusSeconds(7), "40.0")
        );

        var thresholds = service.calculateDynamicPriceThresholds(market);

        assertThat(thresholds.cheapPriceThreshold()).isEqualByComparingTo("6.0");
        assertThat(thresholds.expensivePriceThreshold()).isEqualByComparingTo("26.25");
    }

    @Test
    void flatPriceHorizonDoesNotClassifyEveryPointAsBothCheapAndExpensive() {
        Instant start = Instant.parse("2026-01-15T00:00:00Z");

        var thresholds = service.calculateDynamicPriceThresholds(List.of(
                point(start, "10.0"), point(start.plusSeconds(1), "10.0")));

        assertThat(thresholds.cheapPriceThreshold()).isEqualByComparingTo("10.0");
        assertThat(thresholds.expensivePriceThreshold()).isGreaterThan(new BigDecimal("10.0"));
    }

    @Test
    void preheatsBeforeExpensivePeriodAndDischargesDuringIt() {
        Instant start = Instant.parse("2026-01-15T00:00:00Z");
        var request = request(List.of(
                point(start, "3.0"),
                point(start.plus(Duration.ofMinutes(15)), "3.0"),
                point(start.plus(Duration.ofMinutes(30)), "24.0"),
                point(start.plus(Duration.ofMinutes(45)), "24.0")
        ));

        var result = service.simulate(request);

        assertThat(result.points()).extracting(HeatingPlanSimulationService.SimulationPoint::mode)
                .containsExactly(
                        HeatingPlanSimulationService.OperatingMode.PREHEAT,
                        HeatingPlanSimulationService.OperatingMode.PREHEAT,
                        HeatingPlanSimulationService.OperatingMode.DISCHARGE,
                        HeatingPlanSimulationService.OperatingMode.DISCHARGE
                );
        assertThat(result.points().getFirst().floorSetpoint()).isEqualByComparingTo("27.0");
        assertThat(result.points().get(2).floorSetpoint()).isEqualByComparingTo("19.0");
        assertThat(result.energyKwh()).isPositive();
    }

    @Test
    void comfortMinimumOverridesExpensivePrice() {
        Instant start = Instant.parse("2026-01-15T00:00:00Z");
        var original = request(List.of(point(start, "24.0")));
        var request = new HeatingPlanSimulationService.SimulationRequest(
                new BigDecimal("22.0"), new BigDecimal("19.5"),
                original.settings(), original.model(), original.market(), null, true, true);

        var point = service.simulate(request).points().getFirst();

        assertThat(point.mode()).isEqualTo(HeatingPlanSimulationService.OperatingMode.COMFORT_RECOVERY);
        assertThat(point.floorSetpoint()).isEqualByComparingTo("23.0");
    }

    @Test
    void rejectsPreheatTargetAboveAbsoluteFloorMaximum() {
        var settings = settings(new BigDecimal("30.0"), new BigDecimal("28.0"));
        var request = new HeatingPlanSimulationService.SimulationRequest(
                new BigDecimal("22.0"), new BigDecimal("21.0"), settings, model(),
                List.of(point(Instant.parse("2026-01-15T00:00:00Z"), "3.0")), null, true, true);

        assertThatThrownBy(() -> service.simulate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute floor maximum");
    }

    private HeatingPlanSimulationService.SimulationRequest request(
            List<HeatingPlanSimulationService.MarketPoint> market) {
        return new HeatingPlanSimulationService.SimulationRequest(
                new BigDecimal("22.0"), new BigDecimal("21.0"),
                settings(new BigDecimal("27.0"), new BigDecimal("29.0")), model(), market, null, true, true);
    }

    private HeatingPlanSimulationService.Settings settings(BigDecimal preheatMaximum,
                                                               BigDecimal absoluteMaximum) {
        return new HeatingPlanSimulationService.Settings(
                Duration.ofMinutes(15),
                new BigDecimal("5.0"), new BigDecimal("20.0"),
                new BigDecimal("23.0"), preheatMaximum, absoluteMaximum,
                new BigDecimal("19.0"), new BigDecimal("20.0"), new BigDecimal("23.5"),
                new BigDecimal("5.0")
        );
    }

    private HeatingPlanSimulationService.ThermalModel model() {
        return new HeatingPlanSimulationService.ThermalModel(
                new BigDecimal("2.0"), new BigDecimal("1.0"),
                new BigDecimal("0.08"), new BigDecimal("0.015"), new BigDecimal("0.002")
        );
    }

    private HeatingPlanSimulationService.MarketPoint point(Instant time, String price) {
        return new HeatingPlanSimulationService.MarketPoint(
                time, new BigDecimal(price), new BigDecimal("-10.0"), new BigDecimal("3.0"));
    }

    @Test
    void recommendsLightingConfiguredWoodLoadBeforeExpensivePeriod() {
        Instant start = Instant.parse("2026-01-15T16:00:00Z");
        var base = request(List.of(
                point(start, "5.0"),
                point(start.plus(Duration.ofHours(1)), "25.0"),
                point(start.plus(Duration.ofHours(2)), "25.0")
        ));
        var stove = new HeatingPlanSimulationService.WoodStoveSettings(
                true, true, "Normal basket", new BigDecimal("8.0"),
                Duration.ofMinutes(45), Duration.ofHours(6), new BigDecimal("0.40"),
                new BigDecimal("0.0"),
                List.of(new HeatingPlanSimulationService.StoveAvailability(
                        start, start.plus(Duration.ofHours(12))))
        );
        var request = new HeatingPlanSimulationService.SimulationRequest(
                base.initialFloorTemperature(), base.initialRoomTemperature(), base.settings(),
                base.model(), base.market(), stove, true, true
        );

        var result = service.simulate(request);

        assertThat(result.woodStoveRecommendation()).isNotNull();
        assertThat(result.woodStoveRecommendation().notifyAt())
                .isEqualTo(Instant.parse("2026-01-15T16:15:00Z"));
        assertThat(result.woodStoveRecommendation().releaseStartsAt())
                .isEqualTo(Instant.parse("2026-01-15T17:00:00Z"));
        assertThat(result.points()).extracting(HeatingPlanSimulationService.SimulationPoint::woodRoomHeatingRate)
                .anyMatch(rate -> rate.signum() > 0);
        assertThat(result.points().stream()
                .filter(point -> point.woodRoomHeatingRate().signum() > 0)
                .map(HeatingPlanSimulationService.SimulationPoint::reason))
                .allMatch(reason -> reason.contains("wood stove"));
    }

    @Test
    void doesNotRecommendWoodWhenStoveIsNotLoaded() {
        Instant start = Instant.parse("2026-01-15T16:00:00Z");
        var base = request(List.of(point(start.plus(Duration.ofHours(1)), "25.0")));
        var stove = new HeatingPlanSimulationService.WoodStoveSettings(
                true, false, "Normal basket", new BigDecimal("8.0"),
                Duration.ofMinutes(45), Duration.ofHours(6), new BigDecimal("0.40"),
                new BigDecimal("0.0"),
                List.of(new HeatingPlanSimulationService.StoveAvailability(start, start.plus(Duration.ofHours(12))))
        );
        var request = new HeatingPlanSimulationService.SimulationRequest(
                base.initialFloorTemperature(), base.initialRoomTemperature(), base.settings(),
                base.model(), base.market(), stove, true, true
        );

        assertThat(service.simulate(request).woodStoveRecommendation()).isNull();
    }

    @Test
    void keepsOptimizationInactiveWhenForecastStaysAboveActivationTemperature() {
        Instant start = Instant.parse("2026-01-15T16:00:00Z");
        var warmPoint = new HeatingPlanSimulationService.MarketPoint(
                start, new BigDecimal("25.0"), new BigDecimal("8.0"), new BigDecimal("2.0"));
        var request = request(List.of(warmPoint));

        var result = service.simulate(request);

        assertThat(result.plannerActive()).isFalse();
        assertThat(result.woodStoveRecommendation()).isNull();
        assertThat(result.points().getFirst().mode())
                .isEqualTo(HeatingPlanSimulationService.OperatingMode.INACTIVE);
    }

    @Test
    void plansPreheatAcrossTheWholeHorizonWithoutAFixedLookAhead() {
        Instant start = Instant.parse("2026-01-15T00:00:00Z");
        var request = request(List.of(
                point(start, "3.0"),
                point(start.plus(Duration.ofHours(1)), "8.0"),
                point(start.plus(Duration.ofHours(2)), "8.0"),
                point(start.plus(Duration.ofHours(3)), "8.0"),
                point(start.plus(Duration.ofHours(4)), "24.0")
        ));

        var result = service.simulate(request);

        assertThat(result.points().getFirst().mode())
                .isEqualTo(HeatingPlanSimulationService.OperatingMode.PREHEAT);
        assertThat(result.points().getLast().mode())
                .isEqualTo(HeatingPlanSimulationService.OperatingMode.DISCHARGE);
    }

    @Test
    void staleFloorMeasurementDisablesPreheating() {
        Instant start = Instant.parse("2026-01-15T00:00:00Z");
        var original = request(List.of(point(start, "3.0"), point(start.plus(Duration.ofHours(4)), "24.0")));
        var request = new HeatingPlanSimulationService.SimulationRequest(
                original.initialFloorTemperature(), original.initialRoomTemperature(), original.settings(),
                original.model(), original.market(), null, false, true);

        assertThat(service.simulate(request).points())
                .extracting(HeatingPlanSimulationService.SimulationPoint::mode)
                .doesNotContain(HeatingPlanSimulationService.OperatingMode.PREHEAT);
    }

    @Test
    void staleRoomMeasurementDisablesPriceOptimization() {
        Instant start = Instant.parse("2026-01-15T00:00:00Z");
        var original = request(List.of(point(start, "24.0")));
        var staleSettings = new HeatingPlanSimulationService.Settings(
                original.settings().step(), original.settings().cheapPriceThreshold(),
                original.settings().expensivePriceThreshold(), original.settings().normalFloorTemperature(),
                original.settings().maximumPreheatFloorTemperature(), original.settings().absoluteMaximumFloorTemperature(),
                original.settings().dischargeFloorSetpoint(), original.settings().minimumRoomTemperature(),
                original.settings().maximumRoomTemperature(), original.settings().plannerActivationOutdoorTemperature());
        var request = new HeatingPlanSimulationService.SimulationRequest(
                original.initialFloorTemperature(), original.initialRoomTemperature(), staleSettings,
                original.model(), original.market(), null, true, false);

        var point = service.simulate(request).points().getFirst();

        assertThat(point.mode()).isEqualTo(HeatingPlanSimulationService.OperatingMode.INACTIVE);
        assertThat(point.reason()).contains("missing or stale");
    }
}
