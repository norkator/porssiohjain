/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeatingPlannerAutomationServiceTest {
    @Mock HeatingPlannerSettingsRepository settingsRepository;
    @Mock HeatingPlannerRoomRepository roomRepository;
    @Mock NordpoolRepository nordpoolRepository;
    @Mock SiteWeatherRepository weatherRepository;
    @Mock HeatingPlannerMeasurementService measurementService;
    @Mock HeatingPlannerThermalModelService thermalModelService;
    @Mock HeatingPlanSimulationService simulationService;
    @Mock HeatingPlannerPlanService planService;
    @Mock HeatingPlannerActiveControlService activeControlService;

    HeatingPlannerAutomationService service;
    HeatingPlannerSettingsEntity settings;
    HeatingPlannerRoomEntity room;
    Instant now;

    @BeforeEach
    void setUp() {
        service = new HeatingPlannerAutomationService(settingsRepository, roomRepository, nordpoolRepository,
                weatherRepository, measurementService, thermalModelService, simulationService, planService,
                activeControlService);
        now = Instant.parse("2026-01-15T12:00:00Z");
        AccountEntity account = new AccountEntity(); account.setId(7L); account.setMarketIndexName("FI");
        SiteEntity site = new SiteEntity(); site.setId(8L); site.setTimezone("Europe/Helsinki");
        settings = HeatingPlannerSettingsEntity.builder().id(1L).account(account).site(site).enabled(true)
                .activeControlEnabled(true).timezone("Europe/Helsinki").build();
        room = HeatingPlannerRoomEntity.builder().id(10L).settings(settings).account(account).site(site)
                .name("Kitchen").enabled(true).build();
    }

    @Test
    void generatesAndAutomaticallyActivatesReplacementWhenAlreadyOptedIn() {
        NordpoolEntity current = price(now.minusSeconds(900), now.plusSeconds(1), "5.0");
        NordpoolEntity next = price(now.plusSeconds(1), now.plusSeconds(901), "20.0");
        SiteWeatherEntity forecast = SiteWeatherEntity.builder().site(settings.getSite()).forecastTime(now)
                .temperature(new BigDecimal("-5")).windSpeedMs(new BigDecimal("3")).build();
        var fresh = new HeatingPlannerMeasurementService.LatestMeasurement(new BigDecimal("21"), now,
                HeatingPlannerMeasurementService.Freshness.FRESH);
        var model = new HeatingPlanSimulationService.ThermalModel(new BigDecimal("2"), new BigDecimal("0.8"),
                new BigDecimal("0.06"), new BigDecimal("0.012"), new BigDecimal("0.001"));
        var simulation = new HeatingPlanSimulationService.SimulationResult(List.of(), BigDecimal.ZERO,
                BigDecimal.ZERO, null, true, "active");
        when(nordpoolRepository.findPricesBetween(anyString(), any(), any())).thenReturn(List.of(current, next));
        when(weatherRepository.findBySiteAndForecastTimeBetweenOrderByForecastTimeAsc(eq(settings.getSite()), any(), any()))
                .thenReturn(List.of(forecast));
        when(roomRepository.findBySettingsIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(room));
        when(measurementService.latestFreshRoomTemperature(room, now)).thenReturn(fresh);
        when(measurementService.latestFreshFloorTemperature(room, now)).thenReturn(fresh);
        when(thermalModelService.learnAndResolve(eq(7L), eq(8L), eq("Kitchen"), any(), eq(now)))
                .thenReturn(new HeatingPlannerThermalModelService.ModelResolution(model, true, 96,
                        new BigDecimal("0.8"), "learned"));
        when(simulationService.calculateDynamicPriceThresholds(anyList())).thenReturn(
                new HeatingPlanSimulationService.PriceThresholds(
                        new BigDecimal("8.75"), new BigDecimal("16.25")));
        when(simulationService.simulate(any())).thenReturn(simulation);
        when(planService.persistSimulatedPlan(eq(7L), eq(8L), anyMap())).thenReturn(true);
        when(activeControlService.readiness(7L, 8L, now)).thenReturn(
                new HeatingPlannerActiveControlService.Readiness(true, true, List.of(), "version", null, null, null));

        service.generateAndMaybeActivate(settings, now);

        assertThat(settings.getLastAutomaticPlanAt()).isEqualTo(now);
        assertThat(settings.getLastAutomaticActivationAt()).isEqualTo(now);
        assertThat(settings.getLastAutomationError()).isNull();
        verify(simulationService).calculateDynamicPriceThresholds(anyList());
        verify(activeControlService).activate(7L, 8L, now);
    }

    private NordpoolEntity price(Instant start, Instant end, String value) {
        NordpoolEntity price = new NordpoolEntity();
        price.setDeliveryStart(start); price.setDeliveryEnd(end); price.setPriceFi(new BigDecimal(value));
        price.setMarketIndexName("FI");
        return price;
    }
}
