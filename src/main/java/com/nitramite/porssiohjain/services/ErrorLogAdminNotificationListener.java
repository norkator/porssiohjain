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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ErrorLogAdminNotificationListener {

    private static final int MAX_RECENT_ERRORS = 1000;
    private final PushNotificationService pushNotificationService;
    private final boolean enabled;
    private final Duration duplicateCooldown;
    private final int maxPerMinute;
    private final Clock clock;
    private final Map<String, Instant> recentErrors = new LinkedHashMap<>();
    private final ArrayDeque<Instant> recentAlerts = new ArrayDeque<>();
    private final ThreadLocal<Boolean> sendingNotification = ThreadLocal.withInitial(() -> false);
    private AppenderBase<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger rootLogger;
    private ThreadPoolExecutor executor;

    @Autowired
    public ErrorLogAdminNotificationListener(
            PushNotificationService pushNotificationService,
            @Value("${app.push.fcm.enabled:false}") boolean fcmEnabled,
            @Value("${app.push.error-alerts.enabled:true}") boolean alertsEnabled,
            @Value("${app.push.error-alerts.duplicate-cooldown:5m}") Duration duplicateCooldown,
            @Value("${app.push.error-alerts.max-per-minute:10}") int maxPerMinute
    ) {
        this(pushNotificationService, fcmEnabled && alertsEnabled, duplicateCooldown, maxPerMinute, Clock.systemUTC());
    }

    ErrorLogAdminNotificationListener(PushNotificationService pushNotificationService, boolean enabled,
                                     Duration duplicateCooldown, int maxPerMinute, Clock clock) {
        if (duplicateCooldown.isNegative() || duplicateCooldown.isZero() || maxPerMinute < 1) {
            throw new IllegalArgumentException("Error alert cooldown and rate limit must be positive");
        }
        this.pushNotificationService = pushNotificationService;
        this.enabled = enabled;
        this.duplicateCooldown = duplicateCooldown;
        this.maxPerMinute = maxPerMinute;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            startListening(context);
        }
    }

    synchronized void startListening(LoggerContext context) {
        if (!enabled || appender != null) {
            return;
        }
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100), task -> {
                    Thread thread = new Thread(task, "admin-error-push");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                queueNotification(event);
            }
        };
        appender.setName("ADMIN_ERROR_PUSH");
        appender.setContext(context);
        appender.start();
        rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(appender);
    }

    private synchronized void queueNotification(ILoggingEvent event) {
        // Push delivery may itself log errors, including database errors. Never alert on those.
        String loggerName = event.getLoggerName();
        if (executor == null || sendingNotification.get() || !Level.ERROR.equals(event.getLevel())
                || loggerName.equals(PushNotificationService.class.getName())
                || loggerName.startsWith("com.google.firebase.") || loggerName.startsWith("com.google.auth.")) {
            return;
        }

        Instant now = clock.instant();
        recentErrors.entrySet().removeIf(entry -> !entry.getValue().plus(duplicateCooldown).isAfter(now));
        while (!recentAlerts.isEmpty() && !recentAlerts.getFirst().plusSeconds(60).isAfter(now)) {
            recentAlerts.removeFirst();
        }
        IThrowableProxy error = event.getThrowableProxy();
        // Use the message template so changing device IDs/parameters do not evade duplicate suppression.
        String source = PushNotificationService.limitErrorNotificationText(loggerName, 160);
        String key = source + "|" + PushNotificationService.limitErrorNotificationText(event.getMessage(), 400)
                + "|" + (error == null ? "" : error.getClassName());
        if (recentErrors.containsKey(key) || recentAlerts.size() >= maxPerMinute) {
            return;
        }
        // Snapshot on the logging thread; do not retain the event, arguments, or full exception graph.
        String message = PushNotificationService.limitErrorNotificationText(event.getFormattedMessage(), 400);
        String errorSummary = error == null ? "" : PushNotificationService.limitErrorNotificationText(
                error.getClassName() + ": " + error.getMessage(), 400);
        try {
            executor.execute(() -> sendNotification(source, message, errorSummary, now));
        } catch (RejectedExecutionException ignored) {
            // A full queue or shutdown must never block or break the application's logging thread.
            return;
        }
        if (recentErrors.size() >= MAX_RECENT_ERRORS) {
            recentErrors.remove(recentErrors.keySet().iterator().next());
        }
        recentErrors.put(key, now);
        recentAlerts.addLast(now);
    }

    private void sendNotification(String loggerName, String message, String error, Instant detectedAt) {
        sendingNotification.set(true);
        try {
            pushNotificationService.sendSystemErrorLogAdminNotification(loggerName, message, error, detectedAt);
        } catch (Exception notificationError) {
            log.warn("Failed to send error log admin push notification", notificationError);
        } finally {
            sendingNotification.remove();
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (appender != null) {
            rootLogger.detachAppender(appender);
            appender.stop();
            appender = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        recentErrors.clear();
        recentAlerts.clear();
    }
}
