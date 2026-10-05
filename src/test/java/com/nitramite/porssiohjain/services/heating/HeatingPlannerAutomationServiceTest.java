/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.SiteOperationState;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.ControlPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    JdbcDataSource dataSource;
    JdbcTemplate jdbc;

    HeatingPlannerAutomationService service;
    HeatingPlannerSettingsEntity settings;
    HeatingPlannerRoomEntity room;
    Instant now;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:automation-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table automation_evidence (settings_id bigint, message varchar(2048))");
        service = new HeatingPlannerAutomationService(settingsRepository, roomRepository, nordpoolRepository,
                weatherRepository, measurementService, thermalModelService, simulationService, planService,
                activeControlService, new ControlPriceService(nordpoolRepository), new DataSourceTransactionManager(dataSource));
        now = Instant.parse("2026-01-15T12:00:00Z");
        AccountEntity account = new AccountEntity(); account.setId(7L); account.setMarketIndexName("FI");
        SiteEntity site = new SiteEntity(); site.setId(8L); site.setTimezone("Europe/Helsinki");
        site.setOperationState(SiteOperationState.POWER_SAVE);
        settings = HeatingPlannerSettingsEntity.builder().id(1L).account(account).site(site).enabled(true)
                .activeControlEnabled(true).timezone("Europe/Helsinki").build();
        room = HeatingPlannerRoomEntity.builder().id(10L).settings(settings).account(account).site(site)
                .name("Kitchen").enabled(true).build();
    }

    @Test
    void generatesAndAutomaticallyActivatesReplacementWhenAlreadyOptedIn() {
        stubSimulation();
        settings.setLastAutomationError("Previous activation was deferred");
        when(planService.persistSimulatedPlan(eq(7L), eq(8L), anyMap())).thenReturn(true);
        when(activeControlService.activateLatestRecalculatedPlanIfOptedIn(7L, 8L, now))
                .thenReturn(new HeatingPlannerActiveControlService.AutomaticActivationResult(true, null));

        service.generateAndMaybeActivate(settings, now);

        assertThat(settings.getLastAutomaticPlanAt()).isEqualTo(now);
        assertThat(settings.getLastAutomaticActivationAt()).isEqualTo(now);
        assertThat(settings.getLastAutomationError()).isNull();
        verify(simulationService).calculatePriceThresholds(anyList(), eq(new BigDecimal("0.3000")),
                eq(new BigDecimal("0.6500")), eq(new BigDecimal("5.0000")),
                eq(new BigDecimal("20.0000")));
        var request = ArgumentCaptor.forClass(HeatingPlanSimulationService.SimulationRequest.class);
        verify(simulationService).simulate(request.capture());
        assertThat(request.getValue().siteOperationState()).isEqualTo(SiteOperationState.POWER_SAVE);
        verify(activeControlService).activateLatestRecalculatedPlanIfOptedIn(7L, 8L, now);
    }

    @Test
    void commitsGeneratedPlanAndDeferredActivationStatus() {
        stubSimulation();
        String message = "Plan generated. Automatic activation deferred: floor-temperature measurement is stale";
        when(activeControlService.activateLatestRecalculatedPlanIfOptedIn(7L, 8L, now))
                .thenReturn(new HeatingPlannerActiveControlService.AutomaticActivationResult(false, message));
        when(settingsRepository.findEnabledIds()).thenReturn(List.of(1L));
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(planService.persistSimulatedPlan(eq(7L), eq(8L), anyMap())).thenAnswer(invocation -> {
            jdbc.update("insert into automation_evidence values (?, ?)", 1L, "Generated simulated plan");
            return true;
        });
        when(settingsRepository.save(settings)).thenAnswer(invocation -> {
            if (settings.getLastAutomationError() != null)
                jdbc.update("insert into automation_evidence values (?, ?)", 1L, settings.getLastAutomationError());
            return settings;
        });

        service.runEnabledPlanners(now);

        assertThat(settings.getLastAutomaticPlanAt()).isEqualTo(now);
        assertThat(settings.getLastAutomaticActivationAt()).isNull();
        assertThat(settings.getLastAutomationError()).isEqualTo(message);
        assertThat(jdbc.queryForList("select message from automation_evidence", String.class))
                .containsExactly("Generated simulated plan", message);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rollsBackFailedSiteAndPersistsItsErrorWhileOtherSitesCommit(boolean failureAtCommit) {
        var earlier = HeatingPlannerSettingsEntity.builder().id(2L).enabled(true).build();
        var later = HeatingPlannerSettingsEntity.builder().id(3L).enabled(true).build();
        when(settingsRepository.findEnabledIds()).thenReturn(List.of(2L, 1L, 3L));
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(settingsRepository.findById(2L)).thenReturn(Optional.of(earlier));
        when(settingsRepository.findById(3L)).thenReturn(Optional.of(later));
        when(settingsRepository.save(settings)).thenAnswer(invocation -> {
            jdbc.update("insert into automation_evidence values (?, ?)", 1L, settings.getLastAutomationError());
            return settings;
        });
        var runner = spy(service);
        doAnswer(invocation -> {
            HeatingPlannerSettingsEntity current = invocation.getArgument(0);
            jdbc.update("insert into automation_evidence values (?, ?)", current.getId(), "Generated simulated plan");
            if (current.getId().equals(1L)) {
                if (!failureAtCommit) throw new IllegalStateException("Unexpected persistence failure");
                ((ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource)).setRollbackOnly();
            }
            return null;
        }).when(runner).generateAndMaybeActivate(any(), eq(now));

        runner.runEnabledPlanners(now);

        assertThat(jdbc.queryForList("select settings_id from automation_evidence where message = ?",
                Long.class, "Generated simulated plan")).containsExactly(2L, 3L);
        assertThat(jdbc.queryForList("select message from automation_evidence where settings_id = 1", String.class))
                .containsExactly(settings.getLastAutomationError());
        assertThat(settings.getLastAutomationError()).isNotBlank();
        verify(runner).generateAndMaybeActivate(later, now);
    }

    private void stubSimulation() {
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
        settings.setCheapPricePercentile(new BigDecimal("0.3000"));
        settings.setExpensivePricePercentile(new BigDecimal("0.6500"));
        when(simulationService.calculatePriceThresholds(anyList(), eq(new BigDecimal("0.3000")),
                eq(new BigDecimal("0.6500")), eq(new BigDecimal("5.0000")),
                eq(new BigDecimal("20.0000")))).thenReturn(
                new HeatingPlanSimulationService.PriceThresholds(
                        new BigDecimal("5.00"), new BigDecimal("20.00")));
        when(simulationService.simulate(any())).thenReturn(simulation);
    }

    private NordpoolEntity price(Instant start, Instant end, String value) {
        NordpoolEntity price = new NordpoolEntity();
        price.setDeliveryStart(start); price.setDeliveryEnd(end); price.setPriceFi(new BigDecimal(value));
        price.setMarketIndexName("FI");
        return price;
    }
}
