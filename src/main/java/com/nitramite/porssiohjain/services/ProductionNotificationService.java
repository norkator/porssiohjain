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
import com.nitramite.porssiohjain.entity.ProductionNotificationEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import com.nitramite.porssiohjain.entity.enums.ResourceType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionNotificationRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceRepository;
import com.nitramite.porssiohjain.entity.repository.ResourceSharingRepository;
import com.nitramite.porssiohjain.services.models.ProductionNotificationResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
public class ProductionNotificationService {

    private final ProductionNotificationRepository productionNotificationRepository;
    private final ProductionSourceRepository productionSourceRepository;
    private final ResourceSharingRepository resourceSharingRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;
    private final PushNotificationTokenService pushNotificationTokenService;
    private final AccountLimitService accountLimitService;

    public List<ProductionNotificationResponse> getProductionNotifications(Long accountId, Long sourceId) {
        ensureAccessibleSource(accountId, sourceId);
        return productionNotificationRepository.findByProductionSourceIdAndAccountIdOrderByIdAsc(sourceId, accountId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ProductionNotificationResponse createProductionNotification(
            Long accountId,
            Long sourceId,
            String name,
            String description,
            LocalTime activeFrom,
            LocalTime activeTo,
            boolean enabled,
            Double triggerKw
    ) {
        validate(name, activeFrom, activeTo, triggerKw);
        ProductionSourceEntity source = ensureOwnedSource(accountId, sourceId);
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        ProductionNotificationEntity entity = ProductionNotificationEntity.builder()
                .account(account)
                .productionSource(source)
                .name(name.trim())
                .description(description)
                .activeFrom(activeFrom)
                .activeTo(activeTo)
                .enabled(enabled)
                .triggerKw(normalizeTriggerKw(triggerKw))
                .build();

        return mapToResponse(productionNotificationRepository.save(entity));
    }

    public ProductionNotificationResponse updateProductionNotification(
            Long accountId,
            Long sourceId,
            Long notificationId,
            String name,
            String description,
            LocalTime activeFrom,
            LocalTime activeTo,
            boolean enabled,
            Double triggerKw
    ) {
        validate(name, activeFrom, activeTo, triggerKw);
        ProductionNotificationEntity entity = ensureOwnedNotification(accountId, sourceId, notificationId);
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setActiveFrom(activeFrom);
        entity.setActiveTo(activeTo);
        entity.setEnabled(enabled);
        entity.setTriggerKw(normalizeTriggerKw(triggerKw));
        return mapToResponse(productionNotificationRepository.save(entity));
    }

    public void deleteProductionNotification(Long accountId, Long sourceId, Long notificationId) {
        ProductionNotificationEntity entity = ensureOwnedNotification(accountId, sourceId, notificationId);
        productionNotificationRepository.delete(entity);
    }

    public void sendDueNotifications() {
        sendDueNotifications(Instant.now());
    }

    void sendDueNotifications(Instant now) {
        for (ProductionNotificationEntity notification : productionNotificationRepository.findByEnabledTrueOrderByIdAsc()) {
            try {
                sendIfDue(notification, now);
            } catch (Exception e) {
                log.error("Failed to process production notification {}", notification.getId(), e);
            }
        }
    }

    private void sendIfDue(ProductionNotificationEntity notification, Instant now) {
        ProductionSourceEntity source = notification.getProductionSource();
        AccountEntity account = notification.getAccount();
        if (!source.isEnabled() || !hasNotificationDeliveryChannel(account)) {
            return;
        }
        if (source.getCurrentKw() == null || source.getCurrentKw().compareTo(notification.getTriggerKw()) < 0) {
            return;
        }

        ZoneId zone = ZoneId.of(source.getTimezone());
        ZonedDateTime nowLocal = now.atZone(zone);
        if (!isInsideActiveWindow(nowLocal.toLocalTime(), notification.getActiveFrom(), notification.getActiveTo())) {
            return;
        }
        if (wasSentForNotificationDate(notification.getLastSentAt(), nowLocal.toLocalDate(), zone)) {
            return;
        }

        Locale locale = Locale.of(account.getLocale());
        boolean sent = false;
        sent |= trySendEmailNotification(notification, source, account, nowLocal, now, locale);
        sent |= trySendPushNotification(notification, source, account, nowLocal, now, locale);
        if (sent) {
            notification.setLastSentAt(now);
            productionNotificationRepository.save(notification);
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

    private boolean wasSentForNotificationDate(Instant lastSentAt, LocalDate notificationDate, ZoneId zone) {
        return lastSentAt != null && lastSentAt.atZone(zone).toLocalDate().equals(notificationDate);
    }

    private boolean trySendEmailNotification(
            ProductionNotificationEntity notification,
            ProductionSourceEntity source,
            AccountEntity account,
            ZonedDateTime nowLocal,
            Instant now,
            Locale locale
    ) {
        if (!account.isEmailNotificationsEnabled()) {
            return false;
        }
        if (account.getEmail() == null || account.getEmail().isBlank()) {
            log.warn("Production notification {} not sent because account {} has no email", notification.getId(), account.getId());
            return false;
        }
        if (!accountLimitService.tryConsumeWeeklyEmailNotification(account.getId(), now)) {
            log.info("Production notification {} email not sent because account {} reached weekly email notification limit", notification.getId(), account.getId());
            return false;
        }

        try {
            emailService.sendProductionNotificationEmail(
                    account.getEmail(),
                    source.getName(),
                    notification.getName(),
                    notification.getDescription(),
                    source.getCurrentKw(),
                    notification.getTriggerKw(),
                    nowLocal,
                    locale
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to send production notification {} email", notification.getId(), e);
            return false;
        }
    }

    private boolean trySendPushNotification(
            ProductionNotificationEntity notification,
            ProductionSourceEntity source,
            AccountEntity account,
            ZonedDateTime nowLocal,
            Instant now,
            Locale locale
    ) {
        if (!account.isPushNotificationsEnabled()) {
            return false;
        }
        if (!pushNotificationTokenService.hasActivePushToken(account.getId())) {
            log.info("Production notification {} push not sent because account {} has no active push tokens", notification.getId(), account.getId());
            return false;
        }
        if (!accountLimitService.tryConsumeWeeklyPushNotification(account.getId(), now)) {
            log.info("Production notification {} push not sent because account {} reached weekly push notification limit", notification.getId(), account.getId());
            return false;
        }

        try {
            return pushNotificationService.sendProductionNotification(account, source, notification, nowLocal, locale);
        } catch (Exception e) {
            log.error("Failed to send production notification {} push", notification.getId(), e);
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

    private ProductionNotificationEntity ensureOwnedNotification(Long accountId, Long sourceId, Long notificationId) {
        ProductionNotificationEntity entity = productionNotificationRepository.findByIdAndAccountId(notificationId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Production notification not found: " + notificationId));
        if (!entity.getProductionSource().getId().equals(sourceId)) {
            throw new EntityNotFoundException("Production notification not found: " + notificationId);
        }
        return entity;
    }

    private ProductionSourceEntity ensureOwnedSource(Long accountId, Long sourceId) {
        return productionSourceRepository.findByIdAndAccountId(sourceId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Production source not found: " + sourceId));
    }

    private ProductionSourceEntity ensureAccessibleSource(Long accountId, Long sourceId) {
        return productionSourceRepository.findByIdAndAccountId(sourceId, accountId)
                .orElseGet(() -> {
                    if (!resourceSharingRepository.existsByReceiverAccountIdAndResourceTypeAndProductionSourceIdAndEnabledTrue(
                            accountId,
                            ResourceType.PRODUCTION_SOURCE,
                            sourceId
                    )) {
                        throw new EntityNotFoundException("Production source not found: " + sourceId);
                    }
                    return productionSourceRepository.findById(sourceId)
                            .orElseThrow(() -> new EntityNotFoundException("Production source not found: " + sourceId));
                });
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

    private ProductionNotificationResponse mapToResponse(ProductionNotificationEntity entity) {
        return ProductionNotificationResponse.builder()
                .id(entity.getId())
                .sourceId(entity.getProductionSource().getId())
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
