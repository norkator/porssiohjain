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
import com.nitramite.porssiohjain.entity.PushNotificationTokenEntity;
import com.nitramite.porssiohjain.entity.repository.PushNotificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
        String title = messageSource.getMessage("mail.controlNotification.title", null, locale);
        String body = messageSource.getMessage(
                "mail.controlNotification.intro",
                new Object[]{control.getName(), notification.getName()},
                locale
        );
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

    @Transactional
    public boolean sendToAccount(Long accountId, String title, String body, Map<String, String> data) {
        List<PushNotificationTokenEntity> tokens = pushNotificationTokenRepository
                .findByAccountIdAndInvalidatedAtIsNullOrderByUpdatedAtDesc(accountId);
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
