package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.WaterMeterEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.WaterMeterRepository;
import com.nitramite.porssiohjain.entity.repository.WaterMeasurementRepository;
import com.nitramite.porssiohjain.entity.WaterMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import com.nitramite.porssiohjain.entity.enums.WaterMeterNotificationFrequency;
import com.nitramite.porssiohjain.services.watermeters.WaterMeterException;
import com.nitramite.porssiohjain.services.wrmwmd.WrmWmdException;
import com.nitramite.porssiohjain.services.wrmwmd.WrmWmdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaterMeterServiceTest {
    private static final String PORTAL = "https://wmd.wrm-systems.fi/water-company";
    private final WaterMeterRepository repository = mock();
    private final WaterMeasurementRepository measurements = mock();
    private final AccountRepository accounts = mock();
    private final DemoAccountGuard guard = mock();
    private final WrmWmdService wrm = mock();
    private final WaterMeterService service = new WaterMeterService(repository, measurements, accounts, guard, List.of(wrm));
    private WaterMeterEntity meter;

    @BeforeEach
    void setup() {
        meter = new WaterMeterEntity();
        meter.setId(9L);
        meter.setAccount(AccountEntity.builder().id(7L).build());
        meter.setName("Home");
        meter.setPortalUrl(PORTAL);
        meter.setUsername("payer-secret");
        meter.setPassword("meter-secret");
        when(repository.lockByIdAndAccountId(9L, 7L)).thenReturn(Optional.of(meter));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(wrm.provider()).thenReturn(WaterMeterProvider.WRM_SYSTEMS_WMD);
        when(wrm.normalizePortalUrl(anyString())).thenAnswer(call -> new WrmWmdService().normalizePortalUrl(call.getArgument(0)));
    }

    @Test
    void editsKeepBlankCredentialsAndDoNotSendSecretsBackToTheView() {
        var saved = service.save(7L, 9L, settings("Home renamed", PORTAL + "/", "", ""));
        assertEquals("payer-secret", meter.getUsername());
        assertEquals("meter-secret", meter.getPassword());
        assertEquals("Home renamed", saved.name());
        assertFalse(saved.toString().contains("secret"));
        verify(guard).assertWritable(7L);
    }

    @Test
    void changedSourceClearsOldSnapshotButRenamingDoesNot() {
        meter.setLatestTotalCubicMeters(new BigDecimal("140.8"));
        meter.setObservedAt(Instant.now());
        meter.setLastPollAttemptAt(Instant.now());
        service.save(7L, 9L, settings("Renamed", PORTAL, "", ""));
        assertNotNull(meter.getObservedAt());
        service.save(7L, 9L, settings("Renamed", PORTAL, "new-payer", ""));
        assertNull(meter.getLatestTotalCubicMeters());
        assertNull(meter.getObservedAt());
        assertNull(meter.getLastPollAttemptAt());
    }

    @Test
    void accountScopedQueriesProtectReadEditDeleteAndPoll() {
        when(repository.findByAccountIdOrderByNameAscIdAsc(7L)).thenReturn(List.of(meter));
        assertEquals(1, service.list(7L).size());
        assertThrows(WaterMeterException.class, () -> service.save(8L, 9L, settings("Other", PORTAL, "", "")));
        assertThrows(WaterMeterException.class, () -> service.delete(8L, 9L));
        assertThrows(WaterMeterException.class, () -> service.pollNow(8L, 9L));
        verify(repository, never()).delete(any());
        verify(wrm, never()).readLatest(any(), any(), any());
    }

    @Test
    void dailyPollIsDeduplicatedInHelsinkiAndManualReadCanRetry() {
        Instant fetched = Instant.now();
        when(wrm.readLatest(anyString(), anyString(), anyString()))
                .thenReturn(new WrmWmdService.Reading(new BigDecimal("140.8"), fetched));
        Instant todayStart = fetched.atZone(ZoneId.of("Europe/Helsinki")).toLocalDate()
                .atStartOfDay(ZoneId.of("Europe/Helsinki")).toInstant();
        meter.setLastPollAttemptAt(todayStart.minusSeconds(1));
        service.pollDaily(7L, 9L);
        service.pollDaily(7L, 9L);
        verify(wrm, times(1)).readLatest(PORTAL, "payer-secret", "meter-secret");
        assertEquals(fetched, meter.getObservedAt());
        var measurement = org.mockito.ArgumentCaptor.forClass(WaterMeasurementEntity.class);
        verify(measurements).save(measurement.capture());
        assertSame(meter, measurement.getValue().getWaterMeter());
        assertEquals(new BigDecimal("140.8"), measurement.getValue().getTotalCubicMeters());
        assertEquals(fetched, measurement.getValue().getObservedAt());
        service.pollNow(7L, 9L);
        verify(wrm, times(2)).readLatest(anyString(), anyString(), anyString());
    }

    @Test
    void failedDailyPollPreservesLastSuccessfulReadingAndDoesNotRetryAutomaticallyThatDay() {
        Instant old = Instant.now().minusSeconds(86400);
        meter.setLatestTotalCubicMeters(new BigDecimal("140.8"));
        meter.setObservedAt(old);
        when(wrm.readLatest(anyString(), anyString(), anyString())).thenThrow(new WrmWmdException("waterMeters.error.login"));
        var result = service.pollDaily(7L, 9L);
        service.pollDaily(7L, 9L);
        assertEquals(new BigDecimal("140.8"), result.latestTotalCubicMeters());
        assertEquals(old, result.observedAt());
        assertEquals("waterMeters.error.login", result.lastErrorKey());
        verify(wrm, times(1)).readLatest(anyString(), anyString(), anyString());
        verifyNoInteractions(measurements);
    }

    @Test
    void disabledAndDemoMetersCannotBePolled() {
        meter.setEnabled(false);
        service.pollDaily(7L, 9L);
        assertThrows(WaterMeterException.class, () -> service.pollNow(7L, 9L));
        meter.setEnabled(true);
        doThrow(new DemoAccountMutationException()).when(guard).assertWritable(7L);
        assertThrows(DemoAccountMutationException.class, () -> service.pollNow(7L, 9L));
        assertThrows(DemoAccountMutationException.class,
                () -> service.save(7L, 9L, settings("Home", PORTAL, "", "")));
        verifyNoInteractions(wrm);
    }

    @Test
    void historyIsAccountScopedAndDoesNotPollTheProvider() {
        when(repository.existsByIdAndAccountId(9L, 7L)).thenReturn(true);
        var row = new WaterMeasurementEntity();
        row.setId(10L);
        row.setObservedAt(Instant.parse("2026-10-22T09:00:00Z"));
        row.setTotalCubicMeters(new BigDecimal("140.8"));
        when(measurements.findByWaterMeterIdAndWaterMeterAccountIdOrderByObservedAtAscIdAsc(9L, 7L)).thenReturn(List.of(row));
        assertEquals(new BigDecimal("140.8"), service.history(7L, 9L).getFirst().totalCubicMeters());
        assertThrows(WaterMeterException.class, () -> service.history(8L, 9L));
        verify(measurements, never()).findByWaterMeterIdAndWaterMeterAccountIdOrderByObservedAtAscIdAsc(9L, 8L);
        verifyNoInteractions(wrm);
    }

    @Test
    void notificationSettingsPersistAndInvalidSchedulesAreRejected() {
        var schedule = new WaterMeterService.NotificationSettings(true, WaterMeterNotificationFrequency.WEEKLY,
                java.time.LocalTime.of(18, 45), java.time.DayOfWeek.FRIDAY, "Europe/Helsinki");
        var saved = service.save(7L, 9L, new WaterMeterService.Settings("Home", WaterMeterProvider.WRM_SYSTEMS_WMD,
                PORTAL, "", "", true, schedule));
        assertEquals(schedule, saved.notifications());
        assertEquals(5, meter.getPushWeekday());
        for (var invalid : List.of(new WaterMeterService.NotificationSettings(true, null, schedule.time(), schedule.weekday(), schedule.timezone()),
                new WaterMeterService.NotificationSettings(true, schedule.frequency(), java.time.LocalTime.of(12, 0, 1), schedule.weekday(), schedule.timezone()),
                new WaterMeterService.NotificationSettings(true, schedule.frequency(), schedule.time(), schedule.weekday(), "invalid/zone"))) {
            assertThrows(WaterMeterException.class, () -> service.save(7L, 9L, new WaterMeterService.Settings("Home",
                    WaterMeterProvider.WRM_SYSTEMS_WMD, PORTAL, "", "", true, invalid)));
        }
    }

    private WaterMeterService.Settings settings(String name, String portal, String username, String password) {
        return new WaterMeterService.Settings(name, WaterMeterProvider.WRM_SYSTEMS_WMD, portal, username, password,
                true, WaterMeterService.NotificationSettings.defaults());
    }
}
