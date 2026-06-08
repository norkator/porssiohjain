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
import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import com.nitramite.porssiohjain.entity.PowerLimitHistoryEntity;
import com.nitramite.porssiohjain.entity.PowerLimitNotificationEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitHistoryRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitNotificationRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitRepository;
import com.nitramite.porssiohjain.services.models.PowerLimitNotificationResponse;
import com.nitramite.porssiohjain.utils.Utils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PowerLimitNotificationService {

    private final PowerLimitNotificationRepository powerLimitNotificationRepository;
    private final PowerLimitRepository powerLimitRepository;
    private final PowerLimitHistoryRepository powerLimitHistoryRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;
    private final PushNotificationTokenService pushNotificationTokenService;
    private final AccountLimitService accountLimitService;
    private final DemoAccountGuard demoAccountGuard;

    public List<PowerLimitNotificationResponse> getPowerLimitNotifications(Long accountId, Long powerLimitId) {
        ensureOwnedPowerLimit(accountId, powerLimitId);
        return powerLimitNotificationRepository.findByPowerLimitIdAndAccountIdOrderByIdAsc(powerLimitId, accountId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public PowerLimitNotificationResponse createPowerLimitNotification(
            Long accountId,
            Long powerLimitId,
            String name,
            String description,
            LocalTime activeFrom,
            LocalTime activeTo,
            boolean enabled,
            Double triggerKw
    ) {
        demoAccountGuard.assertWritable(accountId);
        validate(name, activeFrom, activeTo, triggerKw);
        PowerLimitEntity powerLimit = ensureOwnedPowerLimit(accountId, powerLimitId);
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        PowerLimitNotificationEntity entity = PowerLimitNotificationEntity.builder()
                .account(account)
                .powerLimit(powerLimit)
                .name(name.trim())
                .description(description)
                .activeFrom(activeFrom)
                .activeTo(activeTo)
                .enabled(enabled)
                .triggerKw(normalizeTriggerKw(triggerKw))
                .build();

        return mapToResponse(powerLimitNotificationRepository.save(entity));
    }

    public PowerLimitNotificationResponse updatePowerLimitNotification(
            Long accountId,
            Long powerLimitId,
            Long notificationId,
            String name,
            String description,
            LocalTime activeFrom,
            LocalTime activeTo,
            boolean enabled,
            Double triggerKw
    ) {
        demoAccountGuard.assertWritable(accountId);
        validate(name, activeFrom, activeTo, triggerKw);
        PowerLimitNotificationEntity entity = ensureOwnedNotification(accountId, powerLimitId, notificationId);
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setActiveFrom(activeFrom);
        entity.setActiveTo(activeTo);
        entity.setEnabled(enabled);
        entity.setTriggerKw(normalizeTriggerKw(triggerKw));
        return mapToResponse(powerLimitNotificationRepository.save(entity));
    }

    public void deletePowerLimitNotification(Long accountId, Long powerLimitId, Long notificationId) {
        demoAccountGuard.assertWritable(accountId);
        PowerLimitNotificationEntity entity = ensureOwnedNotification(accountId, powerLimitId, notificationId);
        powerLimitNotificationRepository.delete(entity);
    }

    public void sendDueNotifications() {
        sendDueNotifications(Instant.now());
    }

    void sendDueNotifications(Instant now) {
        for (PowerLimitNotificationEntity notification : powerLimitNotificationRepository.findByEnabledTrueOrderByIdAsc()) {
            try {
                sendIfDue(notification, now);
            } catch (Exception e) {
                log.error("Failed to process power limit notification {}", notification.getId(), e);
            }
        }
    }

    private void sendIfDue(PowerLimitNotificationEntity notification, Instant now) {
        PowerLimitEntity powerLimit = notification.getPowerLimit();
        AccountEntity account = notification.getAccount();
        if (!powerLimit.isEnabled() || !hasNotificationDeliveryChannel(account)) {
            return;
        }

        ZoneId zone = ZoneId.of(powerLimit.getTimezone());
        ZonedDateTime nowLocal = now.atZone(zone);
        if (!isInsideActiveWindow(nowLocal.toLocalTime(), notification.getActiveFrom(), notification.getActiveTo())) {
            return;
        }
        if (wasSentForNotificationDate(notification.getLastSentAt(), nowLocal.toLocalDate(), zone)) {
            return;
        }

        BigDecimal intervalSum = getCurrentIntervalSum(powerLimit, now, zone);
        if (intervalSum.compareTo(notification.getTriggerKw()) < 0) {
            return;
        }

        Locale locale = Locale.of(account.getLocale());
        boolean sent = false;
        sent |= trySendEmailNotification(notification, powerLimit, intervalSum, account, nowLocal, now, locale);
        sent |= trySendPushNotification(notification, powerLimit, intervalSum, account, nowLocal, now, locale);
        if (sent) {
            notification.setLastSentAt(now);
            powerLimitNotificationRepository.save(notification);
        }
    }

    private BigDecimal getCurrentIntervalSum(PowerLimitEntity powerLimit, Instant now, ZoneId zone) {
        int intervalMinutes = powerLimit.getLimitIntervalMinutes();
        Instant intervalStart = Utils.toInterval(now, zone, intervalMinutes);
        Instant intervalEnd = intervalStart.plus(intervalMinutes, ChronoUnit.MINUTES);
        return powerLimitHistoryRepository
                .findByPowerLimitAndCreatedAtBetween(
                        powerLimit.getAccount().getId(),
                        powerLimit.getId(),
                        intervalStart,
                        intervalEnd
                )
                .stream()
                .map(PowerLimitHistoryEntity::getKilowatts)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private boolean wasSentForNotificationDate(Instant lastSentAt, LocalDate notificationDate, ZoneId zone) {
        return lastSentAt != null && lastSentAt.atZone(zone).toLocalDate().equals(notificationDate);
    }

    private boolean trySendEmailNotification(
            PowerLimitNotificationEntity notification,
            PowerLimitEntity powerLimit,
            BigDecimal currentKw,
            AccountEntity account,
            ZonedDateTime nowLocal,
            Instant now,
            Locale locale
    ) {
        if (!account.isEmailNotificationsEnabled()) {
            return false;
        }
        if (account.getEmail() == null || account.getEmail().isBlank()) {
            log.warn("Power limit notification {} not sent because account {} has no email", notification.getId(), account.getId());
            return false;
        }
        if (!accountLimitService.tryConsumeWeeklyEmailNotification(account.getId(), now)) {
            log.info("Power limit notification {} email not sent because account {} reached weekly email notification limit", notification.getId(), account.getId());
            return false;
        }

        try {
            emailService.sendPowerLimitNotificationEmail(
                    account.getEmail(),
                    powerLimit.getName(),
                    notification.getName(),
                    notification.getDescription(),
                    currentKw,
                    notification.getTriggerKw(),
                    nowLocal,
                    locale
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to send power limit notification {} email", notification.getId(), e);
            return false;
        }
    }

    private boolean trySendPushNotification(
            PowerLimitNotificationEntity notification,
            PowerLimitEntity powerLimit,
            BigDecimal currentKw,
            AccountEntity account,
            ZonedDateTime nowLocal,
            Instant now,
            Locale locale
    ) {
        if (!account.isPushNotificationsEnabled()) {
            return false;
        }
        if (!pushNotificationTokenService.hasActivePushToken(account.getId())) {
            log.info("Power limit notification {} push not sent because account {} has no active push tokens", notification.getId(), account.getId());
            return false;
        }
        if (!accountLimitService.tryConsumeWeeklyPushNotification(account.getId(), now)) {
            log.info("Power limit notification {} push not sent because account {} reached weekly push notification limit", notification.getId(), account.getId());
            return false;
        }

        try {
            return pushNotificationService.sendPowerLimitNotification(account, powerLimit, notification, currentKw, nowLocal, locale);
        } catch (Exception e) {
            log.error("Failed to send power limit notification {} push", notification.getId(), e);
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

    private PowerLimitNotificationEntity ensureOwnedNotification(Long accountId, Long powerLimitId, Long notificationId) {
        PowerLimitNotificationEntity entity = powerLimitNotificationRepository.findByIdAndAccountId(notificationId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Power limit notification not found: " + notificationId));
        if (!entity.getPowerLimit().getId().equals(powerLimitId)) {
            throw new EntityNotFoundException("Power limit notification not found: " + notificationId);
        }
        return entity;
    }

    private PowerLimitEntity ensureOwnedPowerLimit(Long accountId, Long powerLimitId) {
        return powerLimitRepository.findByAccountIdAndId(accountId, powerLimitId)
                .orElseThrow(() -> new EntityNotFoundException("Power limit not found: " + powerLimitId));
    }

    private void validate(String name, LocalTime activeFrom, LocalTime activeTo, Double triggerKw) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Notification name cannot be empty");
        }
        if (activeFrom == null || activeTo == null) {
            throw new IllegalArgumentException("Active time from and to are required");
        }
        if (triggerKw == null || triggerKw < 0) {
            throw new IllegalArgumentException("Trigger kW must be zero or greater");
        }
    }

    private BigDecimal normalizeTriggerKw(Double triggerKw) {
        return triggerKw != null ? BigDecimal.valueOf(triggerKw) : BigDecimal.ZERO;
    }

    private PowerLimitNotificationResponse mapToResponse(PowerLimitNotificationEntity entity) {
        return PowerLimitNotificationResponse.builder()
                .id(entity.getId())
                .powerLimitId(entity.getPowerLimit().getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .activeFrom(entity.getActiveFrom())
                .activeTo(entity.getActiveTo())
                .enabled(entity.isEnabled())
                .triggerKw(entity.getTriggerKw())
                .lastSentAt(entity.getLastSentAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
