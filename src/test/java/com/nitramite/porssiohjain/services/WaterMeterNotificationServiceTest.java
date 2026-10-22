package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.WaterMeterEntity;
import com.nitramite.porssiohjain.entity.enums.WaterMeterNotificationFrequency;
import com.nitramite.porssiohjain.entity.repository.WaterMeterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaterMeterNotificationServiceTest {
    private final WaterMeterRepository meters = mock();
    private final PushNotificationService push = mock();
    private final PushNotificationTokenService tokens = mock();
    private final AccountLimitService limits = mock();
    private final WaterMeterNotificationService service = new WaterMeterNotificationService(meters, push, tokens, limits);
    private WaterMeterEntity meter;
    private static final Instant DUE = Instant.parse("2026-10-22T09:05:45Z");

    @BeforeEach
    void setup() {
        AccountEntity account = AccountEntity.builder().id(7L).pushNotificationsEnabled(true).locale("fi").build();
        meter = new WaterMeterEntity();
        meter.setId(9L);
        meter.setAccount(account);
        meter.setName("Home");
        meter.setPushEnabled(true);
        meter.setLatestTotalCubicMeters(new BigDecimal("140.8"));
        meter.setObservedAt(Instant.parse("2026-10-21T09:00:00Z"));
        when(meters.lockByIdAndAccountId(9L, 7L)).thenReturn(Optional.of(meter));
        when(tokens.hasActivePushToken(7L)).thenReturn(true);
        when(limits.tryConsumeWeeklyPushNotification(eq(7L), any())).thenReturn(true);
        when(push.sendWaterMeterNotification(any(), any(), any())).thenReturn(true);
    }

    @Test
    void usesTheLatestCachedTotalAndDoesNotSendTwiceOnTheSameLocalDay() {
        meter.setLastErrorKey("waterMeters.error.network");
        service.sendIfDue(7L, 9L, DUE);
        service.sendIfDue(7L, 9L, DUE.plusSeconds(1));
        verify(push).sendWaterMeterNotification(meter.getAccount(), meter, DUE.atZone(ZoneId.of("Europe/Helsinki")));
        verify(limits).tryConsumeWeeklyPushNotification(7L, DUE);
        assertEquals(DUE, meter.getLastPushSentAt());
        assertEquals(new BigDecimal("140.8"), meter.getLatestTotalCubicMeters());
        assertEquals(Instant.parse("2026-10-21T09:00:00Z"), meter.getObservedAt());
        verify(meters).save(meter);
    }

    @Test
    void onlyTheSelectedMinuteIsEligibleWithoutCatchUp() {
        service.sendIfDue(7L, 9L, Instant.parse("2026-10-22T09:04:59Z"));
        service.sendIfDue(7L, 9L, Instant.parse("2026-10-22T09:06:00Z"));
        service.sendIfDue(7L, 9L, Instant.parse("2026-10-22T21:05:00Z"));
        verifyNoInteractions(push, tokens, limits);
        assertNull(meter.getLastPushSentAt());
    }

    @Test
    void weeklyUsesTheChosenWeekdayAndIsoWeekEvenAcrossNewYear() {
        meter.setPushFrequency(WaterMeterNotificationFrequency.WEEKLY);
        meter.setPushWeekday(DayOfWeek.FRIDAY.getValue());
        service.sendIfDue(7L, 9L, DUE); // Thursday.
        verifyNoInteractions(push);
        meter.setLastPushSentAt(Instant.parse("2026-12-31T10:05:45Z"));
        service.sendIfDue(7L, 9L, Instant.parse("2027-01-01T10:05:45Z"));
        verifyNoInteractions(push);
        Instant nextWeek = Instant.parse("2027-01-08T10:05:45Z");
        service.sendIfDue(7L, 9L, nextWeek);
        assertEquals(nextWeek, meter.getLastPushSentAt());
        verify(push).sendWaterMeterNotification(any(), any(), eq(nextWeek.atZone(ZoneId.of("Europe/Helsinki"))));
    }

    @Test
    void springGapMovesTheTimeForwardAndAutumnOverlapDeliversOnlyOnce() {
        meter.setPushTime(LocalTime.of(3, 30));
        service.sendIfDue(7L, 9L, Instant.parse("2026-03-29T00:30:45Z"));
        verifyNoInteractions(push);
        service.sendIfDue(7L, 9L, Instant.parse("2026-03-29T01:30:45Z"));
        assertEquals(Instant.parse("2026-03-29T01:30:45Z"), meter.getLastPushSentAt());
        clearInvocations(push);
        meter.setLastPushSentAt(null);
        service.sendIfDue(7L, 9L, Instant.parse("2026-10-25T00:30:45Z"));
        service.sendIfDue(7L, 9L, Instant.parse("2026-10-25T01:30:45Z"));
        verify(push, times(1)).sendWaterMeterNotification(any(), any(), any());
    }

    @Test
    void dailyMidnightUsesTheLocalDayRatherThanUtc() {
        meter.setPushTime(LocalTime.MIDNIGHT);
        meter.setLastPushSentAt(Instant.parse("2026-10-21T09:05:45Z"));
        Instant midnight = Instant.parse("2026-10-21T21:00:45Z");
        service.sendIfDue(7L, 9L, midnight);
        assertEquals(midnight, meter.getLastPushSentAt());
    }

    @Test
    void noDataDisabledMetersDemoAccountsAndDisabledPushPreferencesCannotSend() {
        meter.setLatestTotalCubicMeters(null);
        service.sendIfDue(7L, 9L, DUE);
        meter.setLatestTotalCubicMeters(BigDecimal.ZERO);
        meter.setObservedAt(null);
        service.sendIfDue(7L, 9L, DUE);
        meter.setObservedAt(DUE);
        meter.setEnabled(false);
        service.sendIfDue(7L, 9L, DUE);
        meter.setEnabled(true);
        meter.setPushEnabled(false);
        service.sendIfDue(7L, 9L, DUE);
        meter.setPushEnabled(true);
        meter.getAccount().setDemo(true);
        service.sendIfDue(7L, 9L, DUE);
        meter.getAccount().setDemo(false);
        meter.getAccount().setPushNotificationsEnabled(false);
        service.sendIfDue(7L, 9L, DUE);
        verifyNoInteractions(push, tokens, limits);
    }

    @Test
    void missingTokensAndAccountLimitsGateDelivery() {
        when(tokens.hasActivePushToken(7L)).thenReturn(false);
        service.sendIfDue(7L, 9L, DUE);
        verifyNoInteractions(push, limits);
        when(tokens.hasActivePushToken(7L)).thenReturn(true);
        when(limits.tryConsumeWeeklyPushNotification(7L, DUE)).thenReturn(false);
        service.sendIfDue(7L, 9L, DUE);
        verifyNoInteractions(push);
        assertNull(meter.getLastPushSentAt());
    }

    @Test
    void failedDeliveryDoesNotRecordASuccessAndForeignAccountsCannotSend() {
        when(push.sendWaterMeterNotification(any(), any(), any())).thenReturn(false);
        service.sendIfDue(7L, 9L, DUE);
        assertNull(meter.getLastPushSentAt());
        verify(meters, never()).save(any());
        clearInvocations(push);
        service.sendIfDue(8L, 9L, DUE);
        verifyNoInteractions(push);
    }
}
