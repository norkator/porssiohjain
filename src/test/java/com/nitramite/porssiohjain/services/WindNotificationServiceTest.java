package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.WindNotificationEntity;
import com.nitramite.porssiohjain.entity.enums.WindNotificationRuleType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.WindNotificationRepository;
import com.nitramite.porssiohjain.services.models.WindForecastChartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                .enabled(true)
                .build();
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
