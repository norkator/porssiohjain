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
import com.nitramite.porssiohjain.entity.MarketNotificationEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.MarketNotificationMetric;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.MarketNotificationRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.services.models.MarketNotificationRequest;
import com.nitramite.porssiohjain.services.models.MarketNotificationResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MarketNotificationService {

    private static final BigDecimal DEFAULT_TAX_MULTIPLIER = BigDecimal.ONE.add(
            BigDecimal.valueOf(25.5).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
    );

    private final MarketNotificationRepository marketNotificationRepository;
    private final AccountRepository accountRepository;
    private final NordpoolRepository nordpoolRepository;
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;
    private final PushNotificationTokenService pushNotificationTokenService;
    private final AccountLimitService accountLimitService;

    public List<MarketNotificationResponse> getMarketNotifications(Long accountId) {
        return marketNotificationRepository.findByAccountIdOrderByIdAsc(accountId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public MarketNotificationResponse createMarketNotification(Long accountId, MarketNotificationRequest request) {
        validate(request);
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        MarketNotificationEntity entity = MarketNotificationEntity.builder()
                .account(account)
                .name(request.getName().trim())
                .description(normalizeDescription(request.getDescription()))
                .metric(request.getMetric())
                .comparisonType(request.getComparisonType())
                .thresholdPrice(normalizeThreshold(request.getThresholdPrice()))
                .activeFrom(request.getActiveFrom())
                .activeTo(request.getActiveTo())
                .timezone(normalizeTimezone(request.getTimezone()))
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();

        return mapToResponse(marketNotificationRepository.save(entity));
    }

    public MarketNotificationResponse updateMarketNotification(Long accountId, Long notificationId, MarketNotificationRequest request) {
        validate(request);
        MarketNotificationEntity entity = ensureOwnedNotification(accountId, notificationId);

        entity.setName(request.getName().trim());
        entity.setDescription(normalizeDescription(request.getDescription()));
        entity.setMetric(request.getMetric());
        entity.setComparisonType(request.getComparisonType());
        entity.setThresholdPrice(normalizeThreshold(request.getThresholdPrice()));
        entity.setActiveFrom(request.getActiveFrom());
        entity.setActiveTo(request.getActiveTo());
        entity.setTimezone(normalizeTimezone(request.getTimezone()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setLastSentAt(null);

        return mapToResponse(marketNotificationRepository.save(entity));
    }

    public void deleteMarketNotification(Long accountId, Long notificationId) {
        marketNotificationRepository.delete(ensureOwnedNotification(accountId, notificationId));
    }

    public void sendDueNotifications() {
        sendDueNotifications(Instant.now());
    }

    void sendDueNotifications(Instant now) {
        for (MarketNotificationEntity notification : marketNotificationRepository.findByEnabledTrueAndLastSentAtIsNullOrderByIdAsc()) {
            try {
                sendIfDue(notification, now);
            } catch (Exception e) {
                log.error("Failed to process market notification {}", notification.getId(), e);
            }
        }
    }

    private void sendIfDue(MarketNotificationEntity notification, Instant now) {
        AccountEntity account = notification.getAccount();
        if (!hasNotificationDeliveryChannel(account)) {
            return;
        }

        ZoneId zone = ZoneId.of(notification.getTimezone());
        ZonedDateTime nowLocal = now.atZone(zone);
        if (!isInsideActiveWindow(nowLocal.toLocalTime(), notification.getActiveFrom(), notification.getActiveTo())) {
            return;
        }

        BigDecimal observedPrice = resolveObservedPrice(notification, now, zone);
        if (observedPrice == null || !matchesThreshold(observedPrice, notification.getComparisonType(), notification.getThresholdPrice())) {
            return;
        }

        Locale locale = Locale.of(account.getLocale());
        boolean sent = false;
        sent |= trySendEmailNotification(notification, account, observedPrice, nowLocal, now, locale);
        sent |= trySendPushNotification(notification, account, observedPrice, nowLocal, now, locale);
        if (sent) {
            notification.setLastSentAt(now);
            marketNotificationRepository.save(notification);
        }
    }

    private BigDecimal resolveObservedPrice(MarketNotificationEntity notification, Instant now, ZoneId zone) {
        if (notification.getMetric() == MarketNotificationMetric.CURRENT_PRICE) {
            return nordpoolRepository.findFirstByDeliveryStartLessThanEqualAndDeliveryEndGreaterThan(now, now)
                    .map(price -> toPriceWithTax(price.getPriceFi()))
                    .orElse(null);
        }

        LocalDate today = LocalDate.now(zone);
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<NordpoolEntity> prices = nordpoolRepository.findPricesBetween(start, end);
        if (prices.isEmpty()) {
            return null;
        }

        return prices.stream()
                .map(price -> toPriceWithTax(price.getPriceFi()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prices.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal toPriceWithTax(BigDecimal priceFi) {
        return priceFi.multiply(BigDecimal.valueOf(0.1)).multiply(DEFAULT_TAX_MULTIPLIER);
    }

    private boolean matchesThreshold(BigDecimal observedPrice, ComparisonType comparisonType, BigDecimal thresholdPrice) {
        int compared = observedPrice.compareTo(thresholdPrice);
        return switch (comparisonType) {
            case GREATER_THAN -> compared > 0;
            case LESS_THAN -> compared < 0;
        };
    }

    private boolean trySendEmailNotification(
            MarketNotificationEntity notification,
            AccountEntity account,
            BigDecimal observedPrice,
            ZonedDateTime detectedAt,
            Instant now,
            Locale locale
    ) {
        if (!account.isEmailNotificationsEnabled()) {
            return false;
        }
        if (account.getEmail() == null || account.getEmail().isBlank()) {
            log.warn("Market notification {} not sent because account {} has no email", notification.getId(), account.getId());
            return false;
        }
        if (!accountLimitService.tryConsumeWeeklyEmailNotification(account.getId(), now)) {
            log.info("Market notification {} email not sent because account {} reached weekly email notification limit", notification.getId(), account.getId());
            return false;
        }

        try {
            emailService.sendMarketNotificationEmail(
                    account.getEmail(),
                    notification.getName(),
                    notification.getDescription(),
                    notification.getMetric(),
                    notification.getComparisonType(),
                    observedPrice,
                    notification.getThresholdPrice(),
                    detectedAt,
                    locale
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to send market notification {} email", notification.getId(), e);
            return false;
        }
    }

    private boolean trySendPushNotification(
            MarketNotificationEntity notification,
            AccountEntity account,
            BigDecimal observedPrice,
            ZonedDateTime detectedAt,
            Instant now,
            Locale locale
    ) {
        if (!account.isPushNotificationsEnabled()) {
            return false;
        }
        if (!pushNotificationTokenService.hasActivePushToken(account.getId())) {
            log.info("Market notification {} push not sent because account {} has no active push tokens", notification.getId(), account.getId());
            return false;
        }
        if (!accountLimitService.tryConsumeWeeklyPushNotification(account.getId(), now)) {
            log.info("Market notification {} push not sent because account {} reached weekly push notification limit", notification.getId(), account.getId());
            return false;
        }

        try {
            return pushNotificationService.sendMarketNotification(account, notification, observedPrice, detectedAt, locale);
        } catch (Exception e) {
            log.error("Failed to send market notification {} push", notification.getId(), e);
            return false;
        }
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

    private MarketNotificationEntity ensureOwnedNotification(Long accountId, Long notificationId) {
        return marketNotificationRepository.findByIdAndAccountId(notificationId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Market notification not found: " + notificationId));
    }

    private void validate(MarketNotificationRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Notification name cannot be empty");
        }
        if (request.getMetric() == null) {
            throw new IllegalArgumentException("Market notification metric is required");
        }
        if (request.getComparisonType() == null) {
            throw new IllegalArgumentException("Comparison type is required");
        }
        if (request.getThresholdPrice() == null) {
            throw new IllegalArgumentException("Threshold price is required");
        }
        if (request.getActiveFrom() == null || request.getActiveTo() == null) {
            throw new IllegalArgumentException("Active time from and to are required");
        }
        normalizeTimezone(request.getTimezone());
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private BigDecimal normalizeThreshold(Double thresholdPrice) {
        return BigDecimal.valueOf(thresholdPrice);
    }

    private String normalizeTimezone(String timezone) {
        String value = timezone == null || timezone.isBlank()
                ? ZoneId.systemDefault().getId()
                : timezone.trim();
        ZoneId.of(value);
        return value;
    }

    private MarketNotificationResponse mapToResponse(MarketNotificationEntity entity) {
        return MarketNotificationResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .metric(entity.getMetric())
                .comparisonType(entity.getComparisonType())
                .thresholdPrice(entity.getThresholdPrice())
                .activeFrom(entity.getActiveFrom())
                .activeTo(entity.getActiveTo())
                .timezone(entity.getTimezone())
                .enabled(entity.isEnabled())
                .lastSentAt(entity.getLastSentAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
