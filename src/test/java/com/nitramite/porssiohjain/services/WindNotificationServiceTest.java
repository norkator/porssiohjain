package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.WindNotificationEntity;
import com.nitramite.porssiohjain.entity.enums.WindNotificationRuleType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.WindNotificationRepository;
import com.nitramite.porssiohjain.services.models.WindForecastChartResponse;
import com.nitramite.porssiohjain.services.models.WindNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WindNotificationServiceTest {

    @Mock
    private WindNotificationRepository repository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private FingridService fingridService;

    @Mock
    private EmailService emailService;

    @Mock
    private PushNotificationService pushService;

    @Mock
    private PushNotificationTokenService tokenService;

    @Mock
    private AccountLimitService limitService;

    @Mock
    private DemoAccountGuard demoGuard;

    private WindNotificationService service;

    @BeforeEach
    void setUp() {
        service = new WindNotificationService(
                repository,
                accountRepository,
                fingridService,
                emailService,
                pushService,
                tokenService,
                limitService,
                demoGuard
        );
    }

    @Test
    void sendsWhenTomorrowAverageIsAboveThreshold() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        WindNotificationEntity notification = notification(WindNotificationRuleType.TOMORROW_AVERAGE_ABOVE, "3000");
        when(repository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));
        when(fingridService.getWindForecastChart("UTC")).thenReturn(chart("1800", "4200", "0"));
        when(limitService.tryConsumeWeeklyEmailNotification(1L, now)).thenReturn(true);

        service.sendDueNotifications(now);

        verify(emailService).sendWindNotificationEmail(
                any(),
                any(),
                any(),
                eq(WindNotificationRuleType.TOMORROW_AVERAGE_ABOVE.name()),
                eq(new BigDecimal("4200")),
                eq(new BigDecimal("3000")),
                any(),
                any(),
                any(ZonedDateTime.class)
        );
    }

    @Test
    void doesNotSendAboveRuleWhenTomorrowAverageIsBelowThreshold() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        WindNotificationEntity notification = notification(WindNotificationRuleType.TOMORROW_AVERAGE_ABOVE, "3000");
        when(repository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));
        when(fingridService.getWindForecastChart("UTC")).thenReturn(chart("1800", "2500", "0"));

        service.sendDueNotifications(now);

        verify(emailService, never()).sendWindNotificationEmail(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(ZonedDateTime.class)
        );
    }

    @Test
    void sendsExistingBelowAndDropRulesWithExpectedObservedValues() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        WindNotificationEntity below = notification(WindNotificationRuleType.TOMORROW_AVERAGE_BELOW, "3000");
        below.setId(2L);
        WindNotificationEntity drop = notification(WindNotificationRuleType.TOMORROW_DROP_PERCENT, "20");
        drop.setId(3L);
        when(repository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(below, drop));
        when(fingridService.getWindForecastChart("UTC")).thenReturn(chart("4000", "2500", "37.5"));
        when(limitService.tryConsumeWeeklyEmailNotification(1L, now)).thenReturn(true);

        service.sendDueNotifications(now);

        verify(emailService).sendWindNotificationEmail(
                any(),
                any(),
                any(),
                eq(WindNotificationRuleType.TOMORROW_AVERAGE_BELOW.name()),
                eq(new BigDecimal("2500")),
                eq(new BigDecimal("3000")),
                any(),
                any(),
                any(ZonedDateTime.class)
        );
        verify(emailService).sendWindNotificationEmail(
                any(),
                any(),
                any(),
                eq(WindNotificationRuleType.TOMORROW_DROP_PERCENT.name()),
                eq(new BigDecimal("37.5")),
                eq(new BigDecimal("20")),
                any(),
                any(),
                any(ZonedDateTime.class)
        );
    }

    private WindNotificationEntity notification(WindNotificationRuleType ruleType, String threshold) {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setEmail("user@example.com");
        account.setLocale("en");
        account.setEmailNotificationsEnabled(true);
        account.setPushNotificationsEnabled(false);

        return WindNotificationEntity.builder()
                .id(1L)
                .account(account)
                .name("Wind forecast")
                .description("Wind condition matched")
                .ruleType(ruleType)
                .threshold(new BigDecimal(threshold))
                .timezone("UTC")
                .notificationTime(LocalTime.of(10, 0))
                .enabled(true)
                .build();
    }

    @Test
    void doesNotEvaluateRulesAtNightBeforeTheChosenTimeOrAfterItsMinute() {
        var notification = notification(WindNotificationRuleType.TOMORROW_AVERAGE_ABOVE, "3000");
        when(repository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));

        service.sendDueNotifications(Instant.parse("2026-01-01T00:05:00Z"));
        service.sendDueNotifications(Instant.parse("2026-01-01T09:59:59Z"));
        service.sendDueNotifications(Instant.parse("2026-01-01T10:01:00Z"));
        service.sendDueNotifications(Instant.parse("2026-01-01T23:00:00Z"));

        verifyNoInteractions(fingridService, emailService, pushService, limitService);
        assertNull(notification.getLastSentAt());
    }

    @Test
    void sendsPushAtTheChosenLocalMinuteAndDoesNotSendTwice() {
        Instant now = Instant.parse("2026-07-01T15:00:30Z");
        var notification = notification(WindNotificationRuleType.TOMORROW_AVERAGE_ABOVE, "3000");
        notification.setTimezone("Europe/Helsinki");
        notification.setNotificationTime(LocalTime.of(18, 0));
        var account = notification.getAccount();
        account.setEmailNotificationsEnabled(false);
        account.setPushNotificationsEnabled(true);
        when(repository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));
        when(fingridService.getWindForecastChart("Europe/Helsinki")).thenReturn(chart("1800", "4200", "0"));
        when(tokenService.hasActivePushToken(1L)).thenReturn(true);
        when(limitService.tryConsumeWeeklyPushNotification(1L, now)).thenReturn(true);
        when(pushService.sendWindNotification(eq(account), eq(notification), any(), any(), any(), any())).thenReturn(true);

        service.sendDueNotifications(now);
        service.sendDueNotifications(now.plusSeconds(10));

        verify(pushService).sendWindNotification(account, notification, new BigDecimal("4200"),
                new BigDecimal("1800"), new BigDecimal("4200"), now.atZone(java.time.ZoneId.of("Europe/Helsinki")));
        verifyNoInteractions(emailService);
        assertEquals(now, notification.getLastSentAt());
        verify(repository).save(notification);
    }

    @Test
    void springDstGapMovesTheSelectedTimeForwardAndDoesNotSendBeforeIt() {
        var notification = notification(WindNotificationRuleType.TOMORROW_AVERAGE_ABOVE, "3000");
        notification.setTimezone("Europe/Helsinki");
        notification.setNotificationTime(LocalTime.of(3, 30));
        when(repository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));

        service.sendDueNotifications(Instant.parse("2026-03-29T00:30:00Z"));
        verifyNoInteractions(fingridService);

        Instant shiftedTime = Instant.parse("2026-03-29T01:30:00Z");
        when(fingridService.getWindForecastChart("Europe/Helsinki")).thenReturn(chart("1800", "4200", "0"));
        when(limitService.tryConsumeWeeklyEmailNotification(1L, shiftedTime)).thenReturn(true);
        service.sendDueNotifications(shiftedTime);

        assertEquals(shiftedTime, notification.getLastSentAt());
        verify(emailService).sendWindNotificationEmail(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingTomorrowForecastDoesNotSendAtTheSelectedTime() {
        var notification = notification(WindNotificationRuleType.TOMORROW_AVERAGE_ABOVE, "3000");
        when(repository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));
        when(fingridService.getWindForecastChart("UTC"))
                .thenReturn(WindForecastChartResponse.builder().todayAverage(new BigDecimal("1800")).build());

        service.sendDueNotifications(Instant.parse("2026-01-01T10:00:00Z"));

        verifyNoInteractions(emailService, pushService, limitService);
        assertNull(notification.getLastSentAt());
    }

    @Test
    void creationPersistsAndReturnsTheSelectedTimeAndDefaultsToNineWhenOmitted() {
        var request = request();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(new AccountEntity()));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        request.setNotificationTime(LocalTime.of(18, 45));

        assertEquals(LocalTime.of(18, 45), service.create(1L, request).getNotificationTime());

        request.setNotificationTime(null);
        assertEquals(LocalTime.of(9, 0), service.create(1L, request).getNotificationTime());
    }

    @Test
    void updatingATimePersistsItAndOlderClientsDoNotResetIt() {
        var notification = notification(WindNotificationRuleType.TOMORROW_AVERAGE_ABOVE, "3000");
        when(repository.findByIdAndAccountId(1L, 1L)).thenReturn(Optional.of(notification));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = request();
        request.setNotificationTime(LocalTime.of(17, 15));

        assertEquals(LocalTime.of(17, 15), service.update(1L, 1L, request).getNotificationTime());
        request.setNotificationTime(null);
        assertEquals(LocalTime.of(17, 15), service.update(1L, 1L, request).getNotificationTime());
    }

    @Test
    void rejectsNotificationTimesWithSeconds() {
        var request = request();
        request.setNotificationTime(LocalTime.of(9, 0, 30));

        assertThrows(IllegalArgumentException.class, () -> service.create(1L, request));
        verifyNoInteractions(repository, accountRepository);
    }

    private WindNotificationRequest request() {
        var request = new WindNotificationRequest();
        request.setName("Wind forecast");
        request.setRuleType(WindNotificationRuleType.TOMORROW_AVERAGE_ABOVE);
        request.setThreshold(3000.0);
        request.setTimezone("UTC");
        return request;
    }

    private WindForecastChartResponse chart(String todayAverage, String tomorrowAverage, String tomorrowDropPercent) {
        return WindForecastChartResponse.builder()
                .timezone("UTC")
                .todayAverage(new BigDecimal(todayAverage))
                .tomorrowAverage(new BigDecimal(tomorrowAverage))
                .tomorrowDropPercent(new BigDecimal(tomorrowDropPercent))
                .points(List.of())
                .build();
    }
}
