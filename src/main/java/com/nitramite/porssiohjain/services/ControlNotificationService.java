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
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.enums.Status;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ControlNotificationRepository;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ControlNotificationService {

    private final ControlNotificationRepository controlNotificationRepository;
    private final ControlRepository controlRepository;
    private final AccountRepository accountRepository;
    private final ControlTableRepository controlTableRepository;
    private final NordpoolRepository nordpoolRepository;
    private final EmailService emailService;

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
        ControlNotificationEntity entity = controlNotificationRepository.findByIdAndAccountId(notificationId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Control notification not found: " + notificationId));

        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setActiveFrom(activeFrom);
        entity.setActiveTo(activeTo);
        entity.setEnabled(enabled);
        entity.setCheapestHours(normalizeCheapestHours(cheapestHours));
        entity.setSendEarlierMinutes(normalizeSendEarlierMinutes(sendEarlierMinutes));

        return mapToResponse(controlNotificationRepository.save(entity));
    }

    public void deleteControlNotification(Long accountId, Long notificationId) {
        ControlNotificationEntity entity = controlNotificationRepository.findByIdAndAccountId(notificationId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Control notification not found: " + notificationId));
        controlNotificationRepository.delete(entity);
    }

    public void sendDueNotifications() {
        Instant now = Instant.now();
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
        if (!isInsideActiveWindow(matchLocal.toLocalTime(), notification.getActiveFrom(), notification.getActiveTo())) {
            return;
        }

        if (!isInsideCheapestHoursWindow(notification, matchLocal)) {
            return;
        }

        if (wasSentForNotificationDate(notification.getLastSentAt(), matchLocal.toLocalDate(), zone, sendEarlierMinutes)) {
            return;
        }

        boolean controlActive = controlTableRepository.existsActiveAt(control.getId(), Status.FINAL, matchTime);
        if (!controlActive) {
            return;
        }

        AccountEntity account = notification.getAccount();
        if (account.getEmail() == null || account.getEmail().isBlank()) {
            log.warn("Control notification {} not sent because account {} has no email", notification.getId(), account.getId());
            return;
        }

        emailService.sendControlNotificationEmail(
                account.getEmail(),
                control.getName(),
                notification.getName(),
                notification.getDescription(),
                nowLocal,
                Locale.of(account.getLocale())
        );
        notification.setLastSentAt(now);
        controlNotificationRepository.save(notification);
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

    private boolean isInsideCheapestHoursWindow(ControlNotificationEntity notification, ZonedDateTime nowLocal) {
        BigDecimal cheapestHours = notification.getCheapestHours();
        if (cheapestHours == null || cheapestHours.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }

        ActiveWindow activeWindow = resolveActiveWindow(nowLocal, notification.getActiveFrom(), notification.getActiveTo());
        long activeWindowMinutes = Duration.between(activeWindow.start(), activeWindow.end()).toMinutes();
        int periodsToUse = (int) Math.ceil(cheapestHours.doubleValue() * 4);
        int maxPeriods = (int) Math.ceil(activeWindowMinutes / 15.0);
        periodsToUse = Math.min(periodsToUse, maxPeriods);
        if (periodsToUse <= 0) {
            return false;
        }

        Instant windowStart = activeWindow.start().toInstant();
        Instant windowEnd = activeWindow.end().toInstant();
        List<NordpoolEntity> cheapestPeriods = nordpoolRepository.findPricesBetween(windowStart, windowEnd.minusNanos(1))
                .stream()
                .filter(price -> !price.getDeliveryStart().isBefore(windowStart))
                .filter(price -> price.getDeliveryStart().isBefore(windowEnd))
                .sorted(Comparator.comparing(NordpoolEntity::getPriceFi)
                        .thenComparing(NordpoolEntity::getDeliveryStart))
                .limit(periodsToUse)
                .toList();

        Instant now = nowLocal.toInstant();
        return cheapestPeriods.stream()
                .anyMatch(price -> !now.isBefore(price.getDeliveryStart()) && now.isBefore(price.getDeliveryEnd()));
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
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private record ActiveWindow(ZonedDateTime start, ZonedDateTime end) {
    }
}
