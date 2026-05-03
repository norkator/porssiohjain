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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlNotificationEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import com.nitramite.porssiohjain.entity.enums.Status;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ControlNotificationRepository;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlNotificationServiceTest {

    @Mock
    private ControlNotificationRepository controlNotificationRepository;

    @Mock
    private ControlRepository controlRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ControlTableRepository controlTableRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private AccountLimitService accountLimitService;

    private ControlNotificationService controlNotificationService;

    @BeforeEach
    void setUp() {
        controlNotificationService = new ControlNotificationService(
                controlNotificationRepository,
                controlRepository,
                accountRepository,
                controlTableRepository,
                emailService,
                accountLimitService
        );
    }

    @Test
    void cheapestHoursRequiresContinuousControlTableRun() {
        Instant now = Instant.parse("2026-01-01T10:00:15Z");
        ControlNotificationEntity notification = notification(now);

        when(controlNotificationRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));
        when(controlTableRepository.findActivePeriodsOverlapping(eq(1L), eq(Status.FINAL), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        activePeriod(1L, now, now.plusSeconds(15 * 60)),
                        activePeriod(2L, now.plusSeconds(30 * 60), now.plusSeconds(45 * 60)),
                        activePeriod(3L, now.plusSeconds(60 * 60), now.plusSeconds(75 * 60)),
                        activePeriod(4L, now.plusSeconds(90 * 60), now.plusSeconds(105 * 60))
                ));

        controlNotificationService.sendDueNotifications(now);

        verify(emailService, never()).sendControlNotificationEmail(
                any(),
                any(),
                any(),
                any(),
                any(ZonedDateTime.class),
                any()
        );
    }

    @Test
    void cheapestHoursAllowsContinuousControlTableRun() {
        Instant now = Instant.parse("2026-01-01T10:00:15Z");
        ControlNotificationEntity notification = notification(now);

        when(controlNotificationRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));
        when(controlTableRepository.findActivePeriodsOverlapping(eq(1L), eq(Status.FINAL), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        activePeriod(1L, now, now.plusSeconds(15 * 60)),
                        activePeriod(2L, now.plusSeconds(15 * 60), now.plusSeconds(30 * 60)),
                        activePeriod(3L, now.plusSeconds(30 * 60), now.plusSeconds(45 * 60)),
                        activePeriod(4L, now.plusSeconds(45 * 60), now.plusSeconds(60 * 60))
                ));
        when(controlTableRepository.existsActiveAt(1L, Status.FINAL, now)).thenReturn(true);
        when(accountLimitService.tryConsumeWeeklyEmailNotification(1L, now)).thenReturn(true);

        controlNotificationService.sendDueNotifications(now);

        verify(emailService).sendControlNotificationEmail(
                eq("user@example.com"),
                eq("Control"),
                eq("Notification"),
                eq("Description"),
                any(ZonedDateTime.class),
                any()
        );
    }

    @Test
    void weeklyLimitBlocksControlNotificationSend() {
        Instant now = Instant.parse("2026-01-01T10:00:15Z");
        ControlNotificationEntity notification = notification(now);

        when(controlNotificationRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(notification));
        when(controlTableRepository.findActivePeriodsOverlapping(eq(1L), eq(Status.FINAL), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        activePeriod(1L, now, now.plusSeconds(15 * 60)),
                        activePeriod(2L, now.plusSeconds(15 * 60), now.plusSeconds(30 * 60)),
                        activePeriod(3L, now.plusSeconds(30 * 60), now.plusSeconds(45 * 60)),
                        activePeriod(4L, now.plusSeconds(45 * 60), now.plusSeconds(60 * 60))
                ));
        when(controlTableRepository.existsActiveAt(1L, Status.FINAL, now)).thenReturn(true);
        when(accountLimitService.tryConsumeWeeklyEmailNotification(1L, now)).thenReturn(false);

        controlNotificationService.sendDueNotifications(now);

        verify(emailService, never()).sendControlNotificationEmail(
                any(),
                any(),
                any(),
                any(),
                any(ZonedDateTime.class),
                any()
        );
    }

    private ControlNotificationEntity notification(Instant now) {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setEmail("user@example.com");
        account.setLocale("en");
        account.setEmailNotificationsEnabled(true);

        ControlEntity control = new ControlEntity();
        control.setId(1L);
        control.setName("Control");
        control.setTimezone("UTC");

        return ControlNotificationEntity.builder()
                .id(1L)
                .account(account)
                .control(control)
                .name("Notification")
                .description("Description")
                .activeFrom(LocalTime.of(0, 0))
                .activeTo(LocalTime.of(23, 59))
                .enabled(true)
                .cheapestHours(BigDecimal.ONE)
                .sendEarlierMinutes(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private ControlTableEntity activePeriod(Long id, Instant start, Instant end) {
        ControlEntity control = new ControlEntity();
        control.setId(1L);
        return ControlTableEntity.builder()
                .id(id)
                .control(control)
                .startTime(start)
                .endTime(end)
                .priceSnt(BigDecimal.ONE)
                .status(Status.FINAL)
                .build();
    }
}
