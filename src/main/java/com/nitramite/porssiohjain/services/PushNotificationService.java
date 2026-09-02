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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlNotificationEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.MarketNotificationEntity;
import com.nitramite.porssiohjain.entity.WindNotificationEntity;
import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import com.nitramite.porssiohjain.entity.PowerLimitNotificationEntity;
import com.nitramite.porssiohjain.entity.ProductionNotificationEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import com.nitramite.porssiohjain.entity.PushNotificationTokenEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerWoodRecommendationEntity;
import com.nitramite.porssiohjain.entity.repository.PushNotificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private static final String FIREBASE_APP_NAME = "porssiohjain-fcm";
    private static final Set<String> INVALID_TOKEN_ERROR_CODES = Set.of(
            "UNREGISTERED",
            "INVALID_ARGUMENT"
    );

    private final MessageSource messageSource;
    private final PushNotificationTokenRepository pushNotificationTokenRepository;

    @Value("${app.push.fcm.enabled:false}")
    private boolean fcmEnabled;

    @Value("${app.push.fcm.service-account-file:}")
    private String serviceAccountFile;

    public boolean sendPowerLimitExceededNotification(
            AccountEntity account,
            String powerLimitName,
            String powerLimitId,
            String limitKw,
            String currentAvgKw,
            Locale locale
    ) {
        String title = messageSource.getMessage("mail.powerLimitExceeded.title", null, locale);
        String body = messageSource.getMessage(
                "mail.powerLimitExceeded.intro",
                new Object[]{powerLimitName},
                locale
        );
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "POWER_LIMIT_EXCEEDED");
        data.put("powerLimitName", powerLimitName);
        data.put("powerLimitId", powerLimitId);
        data.put("limitKw", limitKw);
        data.put("currentAvgKw", currentAvgKw);
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendControlNotification(
            AccountEntity account,
            ControlEntity control,
            ControlNotificationEntity notification,
            ZonedDateTime activeSince,
            Locale locale
    ) {
        String title = notification.getName();
        String body = notification.getDescription() == null ? "" : notification.getDescription();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "CONTROL_NOTIFICATION");
        data.put("controlId", String.valueOf(control.getId()));
        data.put("controlName", control.getName());
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("notificationName", notification.getName());
        data.put("description", notification.getDescription() == null ? "" : notification.getDescription());
        data.put("activeSince", activeSince.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendControlActivatedNotification(
            AccountEntity account,
            ControlEntity control,
            ZonedDateTime activeSince,
            Locale locale
    ) {
        String title = messageSource.getMessage("mail.controlActivated.title", null, locale);
        String body = messageSource.getMessage(
                "mail.controlActivated.intro",
                new Object[]{control.getName()},
                locale
        );
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "CONTROL_ACTIVATED");
        data.put("controlId", String.valueOf(control.getId()));
        data.put("controlName", control.getName());
        data.put("activeSince", activeSince.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendProductionNotification(
            AccountEntity account,
            ProductionSourceEntity source,
            ProductionNotificationEntity notification,
            ZonedDateTime detectedAt,
            Locale locale
    ) {
        String title = notification.getName();
        String body = notification.getDescription() == null ? "" : notification.getDescription();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "PRODUCTION_NOTIFICATION");
        data.put("sourceId", String.valueOf(source.getId()));
        data.put("sourceName", source.getName());
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("notificationName", notification.getName());
        data.put("description", notification.getDescription() == null ? "" : notification.getDescription());
        data.put("currentKw", source.getCurrentKw() == null ? "" : source.getCurrentKw().toPlainString());
        data.put("triggerKw", notification.getTriggerKw() == null ? "" : notification.getTriggerKw().toPlainString());
        data.put("detectedAt", detectedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendPowerLimitNotification(
            AccountEntity account,
            PowerLimitEntity powerLimit,
            PowerLimitNotificationEntity notification,
            BigDecimal currentKw,
            ZonedDateTime detectedAt,
            Locale locale
    ) {
        String title = notification.getName();
        String body = notification.getDescription() == null ? "" : notification.getDescription();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "POWER_LIMIT_NOTIFICATION");
        data.put("powerLimitId", String.valueOf(powerLimit.getId()));
        data.put("powerLimitName", powerLimit.getName());
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("notificationName", notification.getName());
        data.put("description", notification.getDescription() == null ? "" : notification.getDescription());
        data.put("currentKw", currentKw == null ? "" : currentKw.toPlainString());
        data.put("triggerKw", notification.getTriggerKw() == null ? "" : notification.getTriggerKw().toPlainString());
        data.put("detectedAt", detectedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendMarketNotification(
            AccountEntity account,
            MarketNotificationEntity notification,
            BigDecimal observedPrice,
            ZonedDateTime detectedAt,
            Locale locale
    ) {
        String title = notification.getName();
        String body = notification.getDescription() == null ? "" : notification.getDescription();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "MARKET_NOTIFICATION");
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("notificationName", notification.getName());
        data.put("description", notification.getDescription() == null ? "" : notification.getDescription());
        data.put("metric", notification.getMetric().name());
        data.put("comparisonType", notification.getComparisonType().name());
        data.put("observedPrice", observedPrice.toPlainString());
        data.put("thresholdPrice", notification.getThresholdPrice().toPlainString());
        data.put("detectedAt", detectedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendWindNotification(AccountEntity account, WindNotificationEntity notification,
                                         BigDecimal observedValue, BigDecimal todayAverage,
                                         BigDecimal tomorrowAverage, ZonedDateTime detectedAt) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "WIND_FORECAST_NOTIFICATION");
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("ruleType", notification.getRuleType().name());
        data.put("observedValue", observedValue.toPlainString());
        data.put("todayAverageMw", todayAverage == null ? "" : todayAverage.toPlainString());
        data.put("tomorrowAverageMw", tomorrowAverage.toPlainString());
        data.put("detectedAt", detectedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return sendToAccount(account.getId(), notification.getName(),
                notification.getDescription() == null ? "" : notification.getDescription(), data);
    }

    public boolean sendDeviceOfflineNotification(
            AccountEntity account,
            DeviceEntity device,
            String offlineSource,
            Instant detectedAt,
            Locale locale
    ) {
        String title = messageSource.getMessage("mail.deviceOffline.title", null, locale);
        String body = messageSource.getMessage(
                "mail.deviceOffline.intro",
                new Object[]{device.getDeviceName()},
                locale
        );
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "DEVICE_OFFLINE");
        data.put("deviceId", String.valueOf(device.getId()));
        data.put("deviceUuid", device.getUuid() == null ? "" : device.getUuid().toString());
        data.put("deviceName", device.getDeviceName());
        data.put("offlineSource", offlineSource);
        data.put("detectedAt", detectedAt.toString());
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendDeviceOnlineNotification(
            AccountEntity account,
            DeviceEntity device,
            String onlineSource,
            Instant detectedAt,
            Locale locale
    ) {
        String title = messageSource.getMessage("mail.deviceOnline.title", null, locale);
        String body = messageSource.getMessage(
                "mail.deviceOnline.intro",
                new Object[]{device.getDeviceName()},
                locale
        );
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "DEVICE_ONLINE");
        data.put("deviceId", String.valueOf(device.getId()));
        data.put("deviceUuid", device.getUuid() == null ? "" : device.getUuid().toString());
        data.put("deviceName", device.getDeviceName());
        data.put("onlineSource", onlineSource);
        data.put("detectedAt", detectedAt.toString());
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendZigbeeGatewayOfflineNotification(
            AccountEntity account, UUID gatewayId, Instant detectedAt, Locale locale) {
        String title = messageSource.getMessage("push.zigbeeGateway.offline.title", null, locale);
        String body = messageSource.getMessage(
                "push.zigbeeGateway.offline.body", new Object[]{gatewayId}, locale);
        Map<String, String> data = zigbeeGatewayData("ZIGBEE_GATEWAY_OFFLINE", gatewayId, detectedAt);
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendZigbeeGatewayOnlineNotification(
            AccountEntity account, UUID gatewayId, Instant detectedAt, Locale locale) {
        String title = messageSource.getMessage("push.zigbeeGateway.online.title", null, locale);
        String body = messageSource.getMessage(
                "push.zigbeeGateway.online.body", new Object[]{gatewayId}, locale);
        Map<String, String> data = zigbeeGatewayData("ZIGBEE_GATEWAY_ONLINE", gatewayId, detectedAt);
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendHeatingPlannerWoodRecommendation(
            AccountEntity account,
            HeatingPlannerWoodRecommendationEntity recommendation,
            ZonedDateTime notifyAt,
            ZonedDateTime releaseStartsAt,
            ZonedDateTime releaseEndsAt,
            Locale locale) {
        String title = messageSource.getMessage("push.heatingPlanner.wood.title", null, locale);
        String body = messageSource.getMessage("push.heatingPlanner.wood.body", new Object[]{
                recommendation.getWoodAmount().stripTrailingZeros().toPlainString(),
                recommendation.getRoom().getName(),
                releaseStartsAt.format(DateTimeFormatter.ofPattern("HH:mm")),
                releaseEndsAt.format(DateTimeFormatter.ofPattern("HH:mm"))
        }, locale);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "HEATING_PLANNER_WOOD_RECOMMENDATION");
        data.put("recommendationId", String.valueOf(recommendation.getId()));
        data.put("siteId", String.valueOf(recommendation.getSite().getId()));
        data.put("roomId", String.valueOf(recommendation.getRoom().getId()));
        data.put("roomName", recommendation.getRoom().getName());
        data.put("woodAmount", recommendation.getWoodAmount().toPlainString());
        data.put("notifyAt", notifyAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        data.put("releaseStartsAt", releaseStartsAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        data.put("releaseEndsAt", releaseEndsAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        data.put("reason", recommendation.getReason());
        return sendToAccount(account.getId(), title, body, data);
    }

    public boolean sendHeatingPlannerWoodRecommendationTest(
            AccountEntity account,
            Long siteId,
            String roomName,
            BigDecimal woodAmount,
            ZonedDateTime notifyAt,
            ZonedDateTime releaseStartsAt,
            ZonedDateTime releaseEndsAt,
            Locale locale) {
        String title = messageSource.getMessage("push.heatingPlanner.wood.title", null, locale);
        String body = messageSource.getMessage("push.heatingPlanner.wood.body", new Object[]{
                woodAmount.stripTrailingZeros().toPlainString(),
                roomName,
                releaseStartsAt.format(DateTimeFormatter.ofPattern("HH:mm")),
                releaseEndsAt.format(DateTimeFormatter.ofPattern("HH:mm"))
        }, locale);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "HEATING_PLANNER_WOOD_RECOMMENDATION");
        data.put("recommendationId", "-1");
        data.put("siteId", String.valueOf(siteId));
        data.put("roomId", "-1");
        data.put("roomName", roomName);
        data.put("woodAmount", woodAmount.toPlainString());
        data.put("notifyAt", notifyAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        data.put("releaseStartsAt", releaseStartsAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        data.put("releaseEndsAt", releaseEndsAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        data.put("reason", "Test notification from Heating Planner");
        data.put("test", "true");
        return sendToAccount(account.getId(), title, body, data);
    }

    private Map<String, String> zigbeeGatewayData(String type, UUID gatewayId, Instant detectedAt) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("gatewayId", gatewayId.toString());
        data.put("detectedAt", detectedAt.toString());
        return data;
    }

    public boolean sendNewAccountCreatedAdminNotification(AccountEntity account, Locale locale) {
        String title = messageSource.getMessage("push.admin.newAccount.title", null, locale);
        String body = messageSource.getMessage(
                "push.admin.newAccount.body",
                new Object[]{account.getUuid()},
                locale
        );
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "NEW_ACCOUNT_CREATED");
        data.put("accountId", String.valueOf(account.getId()));
        data.put("accountUuid", account.getUuid() == null ? "" : account.getUuid().toString());
        data.put("createdAt", account.getCreatedAt() == null ? "" : account.getCreatedAt().toString());
        return sendToAdminAccounts(title, body, data);
    }

    public boolean sendNewDeviceCreatedAdminNotification(DeviceEntity device, Locale locale) {
        AccountEntity account = device.getAccount();
        String title = messageSource.getMessage("push.admin.newDevice.title", null, locale);
        String body = messageSource.getMessage(
                "push.admin.newDevice.body",
                new Object[]{device.getDeviceName(), account == null ? "" : account.getUuid()},
                locale
        );
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "NEW_DEVICE_CREATED");
        data.put("deviceId", String.valueOf(device.getId()));
        data.put("deviceUuid", device.getUuid() == null ? "" : device.getUuid().toString());
        data.put("deviceName", device.getDeviceName());
        data.put("deviceType", device.getDeviceType() == null ? "" : device.getDeviceType().name());
        data.put("devicePlatform", device.getDevicePlatform() == null ? "" : device.getDevicePlatform().name());
        data.put("accountId", account == null ? "" : String.valueOf(account.getId()));
        data.put("accountUuid", account == null || account.getUuid() == null ? "" : account.getUuid().toString());
        data.put("createdAt", device.getCreatedAt() == null ? "" : device.getCreatedAt().toString());
        return sendToAdminAccounts(title, body, data);
    }

    public boolean sendSystemErrorAdminNotification(String context, Throwable error) {
        String errorMessage = error == null ? "" : error.toString();
        String title = "System error";
        String body = context + ": " + errorMessage;
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "SYSTEM_ERROR");
        data.put("context", context);
        data.put("error", errorMessage);
        data.put("detectedAt", Instant.now().toString());
        return sendToAdminAccounts(title, body, data);
    }

    @Transactional
    public boolean sendToAdminAccounts(String title, String body, Map<String, String> data) {
        List<PushNotificationTokenEntity> tokens = pushNotificationTokenRepository
                .findActiveAdminTokensOrderByUpdatedAtDesc();
        return sendToTokens(tokens, title, body, data);
    }

    @Transactional
    public boolean sendToAccount(Long accountId, String title, String body, Map<String, String> data) {
        List<PushNotificationTokenEntity> tokens = pushNotificationTokenRepository
                .findByAccountIdAndInvalidatedAtIsNullOrderByUpdatedAtDesc(accountId);
        return sendToTokens(tokens, title, body, data);
    }

    private boolean sendToTokens(List<PushNotificationTokenEntity> tokens, String title, String body, Map<String, String> data) {
        if (tokens.isEmpty()) {
            return false;
        }

        FirebaseMessaging messaging = getMessaging();
        if (messaging == null) {
            return false;
        }

        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .addAllTokens(tokens.stream().map(PushNotificationTokenEntity::getToken).toList())
                .build();

        try {
            BatchResponse response = messaging.sendEachForMulticast(message);
            invalidateBadTokens(tokens, response);
            return response.getSuccessCount() > 0;
        } catch (FirebaseMessagingException e) {
            throw new IllegalStateException("Failed to send push notification", e);
        }
    }

    private void invalidateBadTokens(List<PushNotificationTokenEntity> tokens, BatchResponse response) {
        List<PushNotificationTokenEntity> invalidTokens = new ArrayList<>();
        List<SendResponse> sendResponses = response.getResponses();
        for (int i = 0; i < sendResponses.size() && i < tokens.size(); i++) {
            SendResponse sendResponse = sendResponses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException exception = sendResponse.getException();
            if (exception == null || !shouldInvalidateToken(exception)) {
                continue;
            }
            PushNotificationTokenEntity token = tokens.get(i);
            token.setInvalidatedAt(java.time.Instant.now());
            invalidTokens.add(token);
        }
        if (!invalidTokens.isEmpty()) {
            pushNotificationTokenRepository.saveAll(invalidTokens);
        }
    }

    private boolean shouldInvalidateToken(FirebaseMessagingException exception) {
        MessagingErrorCode errorCode = exception.getMessagingErrorCode();
        if (errorCode != null) {
            return switch (errorCode) {
                case UNREGISTERED, INVALID_ARGUMENT -> true;
                default -> false;
            };
        }
        String errorCodeString = exception.getErrorCode() != null
                ? exception.getErrorCode().toString()
                : null;
        return errorCodeString != null && INVALID_TOKEN_ERROR_CODES.contains(errorCodeString);
    }

    private FirebaseMessaging getMessaging() {
        FirebaseApp app = getFirebaseApp();
        return app != null ? FirebaseMessaging.getInstance(app) : null;
    }

    private synchronized FirebaseApp getFirebaseApp() {
        if (!fcmEnabled) {
            return null;
        }
        if (serviceAccountFile == null || serviceAccountFile.isBlank()) {
            log.warn("FCM push notifications enabled but service account file is not configured");
            return null;
        }

        for (FirebaseApp app : FirebaseApp.getApps()) {
            if (FIREBASE_APP_NAME.equals(app.getName())) {
                return app;
            }
        }

        Path path = Path.of(serviceAccountFile);
        if (!Files.exists(path)) {
            log.warn("FCM service account file does not exist: {}", serviceAccountFile);
            return null;
        }

        try (var inputStream = Files.newInputStream(path)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(inputStream))
                    .build();
            return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize Firebase app", e);
        }
    }
}
