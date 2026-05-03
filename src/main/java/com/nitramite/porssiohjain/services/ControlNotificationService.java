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
import com.nitramite.porssiohjain.services.models.ControlNotificationResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ControlNotificationService {

    private static final Duration NEXT_SEND_LOOKAHEAD = Duration.ofDays(2);

    private final ControlNotificationRepository controlNotificationRepository;
    private final ControlRepository controlRepository;
    private final AccountRepository accountRepository;
    private final ControlTableRepository controlTableRepository;
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;
    private final PushNotificationTokenService pushNotificationTokenService;
    private final AccountLimitService accountLimitService;

    public List<ControlNotificationResponse> getControlNotifications(Long accountId, Long controlId) {
        ensureOwnedControl(accountId, controlId);
        return controlNotificationRepository.findByControlIdAndAccountIdOrderByIdAsc(controlId, accountId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ControlNotificationResponse createControlNotification(
            Long accountId,
            Long controlId,
            String name,
            String description,
            LocalTime activeFrom,
            LocalTime activeTo,
            boolean enabled,
            Double cheapestHours,
            Integer sendEarlierMinutes
    ) {
        validate(name, activeFrom, activeTo, cheapestHours, sendEarlierMinutes);
        ControlEntity control = ensureOwnedControl(accountId, controlId);
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        ControlNotificationEntity entity = ControlNotificationEntity.builder()
                .account(account)
                .control(control)
                .name(name.trim())
                .description(description)
                .activeFrom(activeFrom)
                .activeTo(activeTo)
                .enabled(enabled)
                .cheapestHours(normalizeCheapestHours(cheapestHours))
                .sendEarlierMinutes(normalizeSendEarlierMinutes(sendEarlierMinutes))
                .build();

        return mapToResponse(controlNotificationRepository.save(entity));
    }

    public ControlNotificationResponse updateControlNotification(
            Long accountId,
            Long controlId,
            Long notificationId,
            String name,
            String description,
            LocalTime activeFrom,
            LocalTime activeTo,
            boolean enabled,
            Double cheapestHours,
            Integer sendEarlierMinutes
    ) {
        validate(name, activeFrom, activeTo, cheapestHours, sendEarlierMinutes);
        ControlNotificationEntity entity = ensureOwnedNotification(accountId, controlId, notificationId);

        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setActiveFrom(activeFrom);
        entity.setActiveTo(activeTo);
        entity.setEnabled(enabled);
        entity.setCheapestHours(normalizeCheapestHours(cheapestHours));
        entity.setSendEarlierMinutes(normalizeSendEarlierMinutes(sendEarlierMinutes));

        return mapToResponse(controlNotificationRepository.save(entity));
    }

    public void deleteControlNotification(Long accountId, Long controlId, Long notificationId) {
        ControlNotificationEntity entity = ensureOwnedNotification(accountId, controlId, notificationId);
        controlNotificationRepository.delete(entity);
    }

    public void sendDueNotifications() {
        sendDueNotifications(Instant.now());
    }

    void sendDueNotifications(Instant now) {
        for (ControlNotificationEntity notification : controlNotificationRepository.findByEnabledTrueOrderByIdAsc()) {
            try {
                sendIfDue(notification, now);
            } catch (Exception e) {
                log.error("Failed to process control notification {}", notification.getId(), e);
            }
        }
    }

    private void sendIfDue(ControlNotificationEntity notification, Instant now) {
        ControlEntity control = notification.getControl();
        ZoneId zone = ZoneId.of(control.getTimezone());
        ZonedDateTime nowLocal = now.atZone(zone);
        int sendEarlierMinutes = normalizeSendEarlierMinutes(notification.getSendEarlierMinutes());
        Instant matchTime = now.plus(Duration.ofMinutes(sendEarlierMinutes));
        ZonedDateTime matchLocal = matchTime.atZone(zone);
        if (!matchesNotificationTime(notification, matchLocal, zone, sendEarlierMinutes)) {
            return;
        }

        AccountEntity account = notification.getAccount();
        Locale locale = Locale.of(account.getLocale());
        boolean sent = false;

        sent |= trySendEmailNotification(notification, nowLocal, now, control, account, locale);
        sent |= trySendPushNotification(notification, nowLocal, now, control, account, locale);

        if (sent) {
            notification.setLastSentAt(now);
            controlNotificationRepository.save(notification);
        }
    }

    private Instant resolveNextSendAt(ControlNotificationEntity notification, Instant now) {
        if (!notification.isEnabled()) {
            return null;
        }
        ControlEntity control = notification.getControl();
        AccountEntity account = notification.getAccount();
        if (!hasNotificationDeliveryChannel(account)) {
            return null;
        }

        ZoneId zone = ZoneId.of(control.getTimezone());
        int sendEarlierMinutes = normalizeSendEarlierMinutes(notification.getSendEarlierMinutes());
        Duration leadTime = Duration.ofMinutes(sendEarlierMinutes);
        Instant firstSchedulerTick = nextSchedulerTick(now);
        Instant matchFrom = firstSchedulerTick.plus(leadTime);
        Instant matchTo = firstSchedulerTick.plus(NEXT_SEND_LOOKAHEAD).plus(leadTime);

        List<ControlTableEntity> activePeriods = controlTableRepository.findActivePeriodsOverlapping(
                control.getId(),
                Status.FINAL,
                matchFrom,
                matchTo
        );

        for (ControlTableEntity activePeriod : activePeriods) {
            Instant firstCandidate = max(firstSchedulerTick, activePeriod.getStartTime().minus(leadTime));
            Instant lastCandidateExclusive = min(firstSchedulerTick.plus(NEXT_SEND_LOOKAHEAD), activePeriod.getEndTime().minus(leadTime));
            Instant candidate = alignToSchedulerTick(firstCandidate);
            while (candidate.isBefore(lastCandidateExclusive)) {
                ZonedDateTime matchLocal = candidate.plus(leadTime).atZone(zone);
                if (matchesNotificationTime(notification, matchLocal, zone, sendEarlierMinutes)) {
                    return candidate;
                }
                candidate = candidate.plus(Duration.ofMinutes(1));
            }
        }
        return null;
    }

    private boolean matchesNotificationTime(
            ControlNotificationEntity notification,
            ZonedDateTime matchLocal,
            ZoneId zone,
            int sendEarlierMinutes
    ) {
        Instant matchTime = matchLocal.toInstant();
        if (!isInsideActiveWindow(matchLocal.toLocalTime(), notification.getActiveFrom(), notification.getActiveTo())) {
            return false;
        }

        if (!isInsideCheapestHoursWindow(notification, matchLocal)) {
            return false;
        }

        if (wasSentForNotificationDate(notification.getLastSentAt(), matchLocal.toLocalDate(), zone, sendEarlierMinutes)) {
            return false;
        }

        return controlTableRepository.existsActiveAt(notification.getControl().getId(), Status.FINAL, matchTime);
    }

    private boolean isInsideActiveWindow(LocalTime now, LocalTime from, LocalTime to) {
        if (from.equals(to)) {
            return true;
        }
        if (from.isBefore(to)) {
            return !now.isBefore(from) && now.isBefore(to);
        }
        return !now.isBefore(from) || now.isBefore(to);
    }

    private boolean wasSentForNotificationDate(Instant lastSentAt, LocalDate notificationDate, ZoneId zone, int sendEarlierMinutes) {
        return lastSentAt != null
                && lastSentAt.plus(Duration.ofMinutes(sendEarlierMinutes)).atZone(zone).toLocalDate().equals(notificationDate);
    }

    private boolean trySendEmailNotification(
            ControlNotificationEntity notification,
            ZonedDateTime nowLocal,
            Instant now,
            ControlEntity control,
            AccountEntity account,
            Locale locale
    ) {
        if (!account.isEmailNotificationsEnabled()) {
            return false;
        }
        if (account.getEmail() == null || account.getEmail().isBlank()) {
            log.warn("Control notification {} not sent because account {} has no email", notification.getId(), account.getId());
            return false;
        }
        if (!accountLimitService.tryConsumeWeeklyEmailNotification(account.getId(), now)) {
            log.info("Control notification {} email not sent because account {} reached weekly email notification limit", notification.getId(), account.getId());
            return false;
        }

        try {
            emailService.sendControlNotificationEmail(
                    account.getEmail(),
                    control.getName(),
                    notification.getName(),
                    notification.getDescription(),
                    nowLocal,
                    locale
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to send control notification {} email", notification.getId(), e);
            return false;
        }
    }

    private boolean trySendPushNotification(
            ControlNotificationEntity notification,
            ZonedDateTime nowLocal,
            Instant now,
            ControlEntity control,
            AccountEntity account,
            Locale locale
    ) {
        if (!account.isPushNotificationsEnabled()) {
            return false;
        }
        if (!pushNotificationTokenService.hasActivePushToken(account.getId())) {
            log.info("Control notification {} push not sent because account {} has no active push tokens", notification.getId(), account.getId());
            return false;
        }
        if (!accountLimitService.tryConsumeWeeklyPushNotification(account.getId(), now)) {
            log.info("Control notification {} push not sent because account {} reached weekly push notification limit", notification.getId(), account.getId());
            return false;
        }

        try {
            return pushNotificationService.sendControlNotification(account, control, notification, nowLocal, locale);
        } catch (Exception e) {
            log.error("Failed to send control notification {} push", notification.getId(), e);
            return false;
        }
    }

    private boolean hasNotificationDeliveryChannel(AccountEntity account) {
        return hasEmailDeliveryChannel(account) || hasPushDeliveryChannel(account);
    }

    private boolean hasEmailDeliveryChannel(AccountEntity account) {
        return account.isEmailNotificationsEnabled()
                && account.getEmail() != null
                && !account.getEmail().isBlank();
    }

    private boolean hasPushDeliveryChannel(AccountEntity account) {
        return account.isPushNotificationsEnabled()
                && pushNotificationTokenService.hasActivePushToken(account.getId());
    }

    private ControlNotificationEntity ensureOwnedNotification(Long accountId, Long controlId, Long notificationId) {
        ControlNotificationEntity entity = controlNotificationRepository.findByIdAndAccountId(notificationId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Control notification not found: " + notificationId));
        if (!entity.getControl().getId().equals(controlId)) {
            throw new EntityNotFoundException("Control notification not found: " + notificationId);
        }
        return entity;
    }

    private boolean isInsideCheapestHoursWindow(ControlNotificationEntity notification, ZonedDateTime matchLocal) {
        BigDecimal cheapestHours = notification.getCheapestHours();
        if (cheapestHours == null || cheapestHours.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }

        ActiveWindow activeWindow = resolveActiveWindow(matchLocal, notification.getActiveFrom(), notification.getActiveTo());
        long requiredMinutes = (long) Math.ceil(cheapestHours.doubleValue() * 60);
        long activeWindowMinutes = Duration.between(activeWindow.start(), activeWindow.end()).toMinutes();
        requiredMinutes = Math.min(requiredMinutes, activeWindowMinutes);
        if (requiredMinutes <= 0) {
            return false;
        }

        Instant windowStart = activeWindow.start().toInstant();
        Instant windowEnd = activeWindow.end().toInstant();
        Instant matchTime = matchLocal.toInstant();
        List<ControlTableEntity> activePeriods = controlTableRepository.findActivePeriodsOverlapping(
                notification.getControl().getId(),
                Status.FINAL,
                windowStart,
                windowEnd
        );

        Instant runStart = null;
        Instant runEnd = null;
        for (ControlTableEntity activePeriod : activePeriods) {
            Instant periodStart = max(activePeriod.getStartTime(), windowStart);
            Instant periodEnd = min(activePeriod.getEndTime(), windowEnd);
            if (!periodStart.isBefore(periodEnd)) {
                continue;
            }

            if (runStart == null || periodStart.isAfter(runEnd)) {
                if (isMatchInsideLongEnoughRun(matchTime, runStart, runEnd, requiredMinutes)) {
                    return true;
                }
                runStart = periodStart;
                runEnd = periodEnd;
            } else if (periodEnd.isAfter(runEnd)) {
                runEnd = periodEnd;
            }
        }

        return isMatchInsideLongEnoughRun(matchTime, runStart, runEnd, requiredMinutes);
    }

    private boolean isMatchInsideLongEnoughRun(Instant matchTime, Instant runStart, Instant runEnd, long requiredMinutes) {
        if (runStart == null || runEnd == null) {
            return false;
        }
        return !matchTime.isBefore(runStart)
                && matchTime.isBefore(runEnd)
                && Duration.between(runStart, runEnd).toMinutes() >= requiredMinutes;
    }

    private ActiveWindow resolveActiveWindow(ZonedDateTime nowLocal, LocalTime from, LocalTime to) {
        ZonedDateTime start = nowLocal.toLocalDate().atTime(from).atZone(nowLocal.getZone());
        ZonedDateTime end = nowLocal.toLocalDate().atTime(to).atZone(nowLocal.getZone());
        if (from.equals(to)) {
            return new ActiveWindow(start, start.plusDays(1));
        }
        if (from.isBefore(to)) {
            return new ActiveWindow(start, end);
        }
        if (nowLocal.toLocalTime().isBefore(to)) {
            return new ActiveWindow(start.minusDays(1), end);
        }
        return new ActiveWindow(start, end.plusDays(1));
    }

    private void validate(String name, LocalTime activeFrom, LocalTime activeTo, Double cheapestHours, Integer sendEarlierMinutes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Notification name cannot be empty");
        }
        if (activeFrom == null || activeTo == null) {
            throw new IllegalArgumentException("Active time from and to are required");
        }
        if (cheapestHours != null && cheapestHours < 0) {
            throw new IllegalArgumentException("Cheapest hours cannot be negative");
        }
        if (sendEarlierMinutes != null && sendEarlierMinutes < 0) {
            throw new IllegalArgumentException("Send earlier minutes cannot be negative");
        }
    }

    private BigDecimal normalizeCheapestHours(Double cheapestHours) {
        return cheapestHours != null ? BigDecimal.valueOf(cheapestHours) : BigDecimal.ZERO;
    }

    private int normalizeSendEarlierMinutes(Integer sendEarlierMinutes) {
        return sendEarlierMinutes != null ? sendEarlierMinutes : 0;
    }

    private Instant nextSchedulerTick(Instant now) {
        Instant currentMinuteTick = now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(15);
        if (now.isAfter(currentMinuteTick)) {
            return currentMinuteTick.plus(Duration.ofMinutes(1));
        }
        return currentMinuteTick;
    }

    private Instant alignToSchedulerTick(Instant instant) {
        Instant tick = instant.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(15);
        if (instant.isAfter(tick)) {
            return tick.plus(Duration.ofMinutes(1));
        }
        return tick;
    }

    private Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private ControlEntity ensureOwnedControl(Long accountId, Long controlId) {
        return controlRepository.findByIdAndAccountId(controlId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found for account with id: " + controlId));
    }

    private ControlNotificationResponse mapToResponse(ControlNotificationEntity entity) {
        return ControlNotificationResponse.builder()
                .id(entity.getId())
                .controlId(entity.getControl().getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .activeFrom(entity.getActiveFrom())
                .activeTo(entity.getActiveTo())
                .enabled(entity.isEnabled())
                .cheapestHours(entity.getCheapestHours())
                .sendEarlierMinutes(entity.getSendEarlierMinutes())
                .lastSentAt(entity.getLastSentAt())
                .nextSendAt(resolveNextSendAt(entity, Instant.now()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private record ActiveWindow(ZonedDateTime start, ZonedDateTime end) {
    }
}
