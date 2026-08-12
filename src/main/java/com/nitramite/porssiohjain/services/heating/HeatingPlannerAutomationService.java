/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.nordpool.NordpoolMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeatingPlannerAutomationService {

    private static final BigDecimal DEFAULT_HEATER_POWER = new BigDecimal("2.0");
    private static final BigDecimal DEFAULT_FLOOR_HEATING_RATE = new BigDecimal("0.8");
    private static final BigDecimal DEFAULT_FLOOR_TO_ROOM_RATE = new BigDecimal("0.06");
    private static final BigDecimal DEFAULT_OUTDOOR_LOSS_RATE = new BigDecimal("0.012");
    private static final BigDecimal DEFAULT_WIND_LOSS_RATE = new BigDecimal("0.001");

    private final HeatingPlannerSettingsRepository settingsRepository;
    private final HeatingPlannerRoomRepository roomRepository;
    private final NordpoolRepository nordpoolRepository;
    private final SiteWeatherRepository weatherRepository;
    private final HeatingPlannerMeasurementService measurementService;
    private final HeatingPlannerThermalModelService thermalModelService;
    private final HeatingPlanSimulationService simulationService;
    private final HeatingPlannerPlanService planService;
    private final HeatingPlannerActiveControlService activeControlService;

    @Transactional
    public void runEnabledPlanners(Instant now) {
        for (HeatingPlannerSettingsEntity settings : settingsRepository.findByEnabledTrueOrderByIdAsc()) {
            try {
                generateAndMaybeActivate(settings, now);
            } catch (RuntimeException exception) {
                settings.setLastAutomationError(truncate(exception.getMessage(), 1024));
                settingsRepository.save(settings);
                log.warn("Heating Planner automation failed for settingsId={}: {}", settings.getId(), exception.getMessage());
            }
        }
    }

    void generateAndMaybeActivate(HeatingPlannerSettingsEntity settings, Instant now) {
        ZoneId zone = zone(settings);
        ZonedDateTime horizonStart = now.atZone(zone).toLocalDate().atStartOfDay(zone);
        ZonedDateTime horizonEnd = horizonStart.plusDays(2);
        List<NordpoolEntity> prices = nordpoolRepository.findPricesBetween(
                NordpoolMarket.normalize(settings.getAccount().getMarketIndexName()),
                horizonStart.toInstant(), horizonEnd.toInstant());
        if (prices.size() < 2 || prices.stream().noneMatch(price -> !price.getDeliveryStart().isAfter(now)
                && price.getDeliveryEnd().isAfter(now))) {
            throw new IllegalStateException("Current Nordpool prices are unavailable");
        }
        List<NordpoolEntity> futurePrices = prices.stream()
                .filter(price -> price.getDeliveryEnd().isAfter(now))
                .toList();
        if (futurePrices.size() < 2) throw new IllegalStateException("Insufficient future Nordpool price coverage");
        List<SiteWeatherEntity> weather = weatherRepository.findBySiteAndForecastTimeBetweenOrderByForecastTimeAsc(
                settings.getSite(), horizonStart.toInstant(), horizonEnd.toInstant());
        if (weather.isEmpty()) throw new IllegalStateException("Weather forecast is unavailable");
        List<HeatingPlanSimulationService.MarketPoint> market = market(settings, futurePrices, weather, zone);

        Map<String, HeatingPlanSimulationService.SimulationResult> results = new LinkedHashMap<>();
        for (HeatingPlannerRoomEntity room : roomRepository
                .findBySettingsIdOrderBySortOrderAscIdAsc(settings.getId()).stream()
                .filter(HeatingPlannerRoomEntity::isEnabled).toList()) {
            var roomMeasurement = measurementService.latestFreshRoomTemperature(room, now);
            var floorMeasurement = measurementService.latestFreshFloorTemperature(room, now);
            BigDecimal initialRoom = roomMeasurement.fresh() ? roomMeasurement.value() : room.getTargetRoomTemperature();
            BigDecimal initialFloor = floorMeasurement.fresh() ? floorMeasurement.value() : room.getNormalFloorTemperature();
            HeatingPlanSimulationService.ThermalModel configured = configuredModel(room);
            HeatingPlanSimulationService.ThermalModel model = thermalModelService.learnAndResolve(
                    settings.getAccount().getId(), settings.getSite().getId(), room.getName(), configured, now).model();
            results.put(room.getName(), simulationService.simulate(request(settings, room, market, model,
                    initialFloor, initialRoom, floorMeasurement.fresh(), roomMeasurement.fresh(), horizonStart)));
        }
        if (results.isEmpty()) throw new IllegalStateException("No enabled rooms are configured");
        if (!planService.persistSimulatedPlan(settings.getAccount().getId(), settings.getSite().getId(), results))
            throw new IllegalStateException("The generated whole-house plan could not be persisted");

        settings.setLastAutomaticPlanAt(now);
        settings.setLastAutomationError(null);
        settingsRepository.save(settings);
        if (settings.isActiveControlEnabled()) {
            try {
                boolean activated = activeControlService.activateLatestRecalculatedPlanIfOptedIn(
                        settings.getAccount().getId(), settings.getSite().getId(), now);
                if (activated) {
                    settings.setLastAutomaticActivationAt(now);
                    settings.setLastAutomationError(null);
                } else {
                    var readiness = activeControlService.readiness(settings.getAccount().getId(),
                            settings.getSite().getId(), now);
                    settings.setLastAutomationError("Plan generated but Heating Planner is currently inactive: "
                            + String.join("; ", readiness.issues()));
                }
            } catch (IllegalStateException ex) {
                settings.setLastAutomationError("Plan generated but automatic activation was rejected: "
                        + ex.getMessage());
            }
            settingsRepository.save(settings);
        }
    }

    private List<HeatingPlanSimulationService.MarketPoint> market(HeatingPlannerSettingsEntity settings,
                                                                  List<NordpoolEntity> prices,
                                                                  List<SiteWeatherEntity> weather, ZoneId zone) {
        BigDecimal taxMultiplier = BigDecimal.ONE.add(settings.getTaxPercent()
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
        return prices.stream().sorted(Comparator.comparing(NordpoolEntity::getDeliveryStart)).map(price -> {
            SiteWeatherEntity forecast = weather.stream().min(Comparator.comparing(point ->
                    Duration.between(point.getForecastTime(), price.getDeliveryStart()).abs())).orElseThrow();
            BigDecimal marketPrice = price.getPriceFi().multiply(new BigDecimal("0.1"))
                    .multiply(taxMultiplier).setScale(4, RoundingMode.HALF_UP);
            BigDecimal transferPrice = transferPrice(settings.getTransferContract(), price.getDeliveryStart(), zone);
            return new HeatingPlanSimulationService.MarketPoint(price.getDeliveryStart(), marketPrice.add(transferPrice),
                    forecast.getTemperature() == null ? BigDecimal.ZERO : forecast.getTemperature(),
                    forecast.getWindSpeedMs() == null ? BigDecimal.ZERO : forecast.getWindSpeedMs());
        }).toList();
    }

    private HeatingPlanSimulationService.SimulationRequest request(HeatingPlannerSettingsEntity settings,
                                                                    HeatingPlannerRoomEntity room,
                                                                    List<HeatingPlanSimulationService.MarketPoint> market,
                                                                    HeatingPlanSimulationService.ThermalModel model,
                                                                    BigDecimal initialFloor, BigDecimal initialRoom,
                                                                    boolean floorFresh, boolean roomFresh,
                                                                    ZonedDateTime horizonStart) {
        HeatingPlanSimulationService.PriceThresholds priceThresholds =
                simulationService.calculateDynamicPriceThresholds(market);
        var simulationSettings = new HeatingPlanSimulationService.Settings(
                Duration.ofMinutes(settings.getSimulationStepMinutes()), priceThresholds.cheapPriceThreshold(),
                priceThresholds.expensivePriceThreshold(), room.getNormalFloorTemperature(),
                room.getMaximumPreheatFloorTemperature(), room.getAbsoluteMaximumFloorTemperature(),
                room.getDischargeFloorSetpoint(), room.getMinimumRoomTemperature(), room.getMaximumRoomTemperature(),
                settings.getPlannerActiveBelowTemperature());
        List<HeatingPlanSimulationService.StoveAvailability> availability = List.of(
                availability(settings, horizonStart), availability(settings, horizonStart.plusDays(1)));
        var stove = new HeatingPlanSimulationService.WoodStoveSettings(true, settings.isStoveLoaded(), "Configured load",
                settings.getWoodAmount(), Duration.ofMinutes(settings.getWoodReleaseDelayMinutes()),
                Duration.ofMinutes(settings.getWoodReleaseDurationMinutes()), new BigDecimal("0.35"),
                settings.getWoodRecommendationBelowTemperature(), availability);
        return new HeatingPlanSimulationService.SimulationRequest(initialFloor, initialRoom, simulationSettings,
                model, market, stove, floorFresh, roomFresh);
    }

    private HeatingPlanSimulationService.StoveAvailability availability(HeatingPlannerSettingsEntity settings,
                                                                         ZonedDateTime day) {
        LocalTime from = settings.getStoveAvailableFrom();
        LocalTime to = settings.getStoveAvailableTo();
        ZonedDateTime starts = day.with(from);
        ZonedDateTime ends = day.with(to);
        if (ends.isBefore(starts)) ends = ends.plusDays(1);
        return new HeatingPlanSimulationService.StoveAvailability(starts.toInstant(), ends.toInstant());
    }

    private HeatingPlanSimulationService.ThermalModel configuredModel(HeatingPlannerRoomEntity room) {
        return new HeatingPlanSimulationService.ThermalModel(
                value(room.getHeaterPowerKw(), DEFAULT_HEATER_POWER),
                value(room.getFloorHeatingRate(), DEFAULT_FLOOR_HEATING_RATE),
                value(room.getFloorToRoomRate(), DEFAULT_FLOOR_TO_ROOM_RATE),
                value(room.getRoomOutdoorLossRate(), DEFAULT_OUTDOOR_LOSS_RATE),
                value(room.getWindLossRate(), DEFAULT_WIND_LOSS_RATE));
    }

    private BigDecimal transferPrice(ElectricityContractEntity contract, Instant time, ZoneId zone) {
        if (contract == null) return BigDecimal.ZERO;
        BigDecimal tax = value(contract.getTaxAmount(), BigDecimal.ZERO);
        if (contract.getStaticPrice() != null && contract.getDayPrice() == null && contract.getNightPrice() == null)
            return contract.getStaticPrice().add(tax);
        boolean night = time.atZone(zone).getHour() >= 22 || time.atZone(zone).getHour() < 7;
        BigDecimal price = night ? contract.getNightPrice() : contract.getDayPrice();
        return price == null ? BigDecimal.ZERO : price.add(tax);
    }

    private ZoneId zone(HeatingPlannerSettingsEntity settings) {
        String timezone = settings.getTimezone();
        return ZoneId.of(timezone == null || timezone.isBlank() ? "Europe/Helsinki" : timezone);
    }

    private BigDecimal value(BigDecimal value, BigDecimal fallback) { return value == null ? fallback : value; }
    private String truncate(String value, int length) {
        String text = value == null || value.isBlank() ? "Unknown automation failure" : value;
        return text.substring(0, Math.min(length, text.length()));
    }
}
