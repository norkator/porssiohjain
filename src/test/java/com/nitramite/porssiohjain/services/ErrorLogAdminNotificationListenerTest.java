package com.nitramite.porssiohjain.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ErrorLogAdminNotificationListenerTest {

    private final PushNotificationService push = mock(PushNotificationService.class);
    private final MutableClock clock = new MutableClock();
    private LoggerContext context;
    private ErrorLogAdminNotificationListener listener;
    private ListAppender<ILoggingEvent> normalLogs;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();
        context.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ALL);
        normalLogs = new ListAppender<>();
        normalLogs.setContext(context);
        normalLogs.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(normalLogs);
        listener = new ErrorLogAdminNotificationListener(push, true, Duration.ofMinutes(5), 10, clock);
        listener.startListening(context);
    }

    @AfterEach
    void tearDown() {
        listener.close();
        context.stop();
    }

    @Test
    void forwardsApplicationAndFrameworkErrorsWithFormattedMessageAndExceptionSummary() {
        context.getLogger("com.nitramite.porssiohjain.services.TestService")
                .error("Device {} failed", 42, new IllegalStateException("Connection refused"));
        context.getLogger("org.springframework.scheduling.support.TaskUtils")
                .error("Unexpected scheduled task failure");

        awaitAlerts(2);
        verify(push).sendSystemErrorLogAdminNotification(
                "com.nitramite.porssiohjain.services.TestService", "Device 42 failed",
                "java.lang.IllegalStateException: Connection refused", clock.instant());
        verify(push).sendSystemErrorLogAdminNotification(
                "org.springframework.scheduling.support.TaskUtils", "Unexpected scheduled task failure",
                "", clock.instant());
        assertEquals(2, normalLogs.list.size());
    }

    @Test
    void ignoresLowerLevelsAndPushInfrastructureErrors() {
        var logger = context.getLogger("test.service");
        logger.info("Started");
        logger.warn("Recoverable error", new RuntimeException("Retrying"));
        context.getLogger(PushNotificationService.class).error("Push failed");
        context.getLogger("com.google.firebase.messaging.FirebaseMessaging").error("FCM failed");
        context.getLogger("com.google.auth.oauth2.GoogleCredentials").error("Credentials failed");
        logger.error("Real error");

        awaitAlerts(1);
        verify(push).sendSystemErrorLogAdminNotification("test.service", "Real error", "", clock.instant());
        assertEquals(6, normalLogs.list.size());
    }

    @Test
    void suppressesChangingArgumentsUntilCooldownExpires() {
        var logger = context.getLogger("test.service");
        logger.error("Device {} failed", 1);
        logger.error("Device {} failed", 2);
        clock.advance(Duration.ofMinutes(5).minusSeconds(1));
        logger.error("Device {} failed", 3);
        clock.advance(Duration.ofSeconds(1));
        logger.error("Device {} failed", 4);

        awaitAlerts(2);
        verify(push, never()).sendSystemErrorLogAdminNotification(anyString(), eq("Device 2 failed"), anyString(), any());
        verify(push, never()).sendSystemErrorLogAdminNotification(anyString(), eq("Device 3 failed"), anyString(), any());
        verify(push).sendSystemErrorLogAdminNotification("test.service", "Device 4 failed", "", clock.instant());
        assertEquals(4, normalLogs.list.size());
    }

    @Test
    void distinctSourcesAndExceptionTypesAreNotDuplicates() {
        context.getLogger("first.service").error("Failed", new IllegalArgumentException("Invalid"));
        context.getLogger("first.service").error("Failed", new IllegalStateException("Unavailable"));
        context.getLogger("second.service").error("Failed", new IllegalStateException("Unavailable"));

        awaitAlerts(3);
    }

    @Test
    void limitsDistinctErrorsAcrossAllLoggersInASlidingMinute() {
        listener.close();
        listener = new ErrorLogAdminNotificationListener(push, true, Duration.ofMinutes(5), 2, clock);
        listener.startListening(context);
        context.getLogger("first.service").error("First");
        clock.advance(Duration.ofSeconds(30));
        context.getLogger("second.service").error("Second");
        context.getLogger("third.service").error("Third");
        clock.advance(Duration.ofSeconds(30));
        context.getLogger("third.service").error("Third");
        context.getLogger("fourth.service").error("Fourth");

        awaitAlerts(3);
        verify(push).sendSystemErrorLogAdminNotification("third.service", "Third", "", clock.instant());
        verify(push, never()).sendSystemErrorLogAdminNotification(eq("fourth.service"), anyString(), anyString(), any());
        assertEquals(5, normalLogs.list.size());
    }

    @Test
    void slowPushDoesNotBlockLoggingAndFullQueueDropsExcessAlerts() throws Exception {
        listener.close();
        listener = new ErrorLogAdminNotificationListener(push, true, Duration.ofMinutes(5), 200, clock);
        listener.startListening(context);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(101);
        AtomicReference<String> workerName = new AtomicReference<>();
        doAnswer(invocation -> {
            workerName.set(Thread.currentThread().getName());
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            completed.countDown();
            return true;
        }).when(push).sendSystemErrorLogAdminNotification(anyString(), anyString(), anyString(), any());
        try {
            context.getLogger("test.service").error("First");
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                for (int i = 0; i < 150; i++) {
                    context.getLogger("test.service").error("Distinct error " + i);
                }
            });
        } finally {
            release.countDown();
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        awaitAlerts(101);
        assertEquals("admin-error-push", workerName.get());
        assertEquals(151, normalLogs.list.size());
    }

    @Test
    void deliveryErrorsDoNotRecurseAndWorkerContinuesAfterFailure() throws Exception {
        CountDownLatch failedDelivery = new CountDownLatch(1);
        doAnswer(invocation -> {
            context.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper").error("Database down");
            failedDelivery.countDown();
            throw new IllegalStateException("Push unavailable");
        }).doReturn(true).when(push).sendSystemErrorLogAdminNotification(anyString(), anyString(), anyString(), any());

        context.getLogger("test.service").error("Original failure");
        assertTrue(failedDelivery.await(2, TimeUnit.SECONDS));
        context.getLogger("test.service").error("Another failure");

        awaitAlerts(2);
        verify(push, never()).sendSystemErrorLogAdminNotification(anyString(), eq("Database down"), anyString(), any());
    }

    @Test
    void registersOnlyOnceAndDetachesWithoutRemovingNormalLogging() {
        listener.startListening(context);
        context.getLogger("test.service").error("Before close");
        awaitAlerts(1);
        listener.close();
        listener.close();
        context.getLogger("test.service").error("After close");

        verifyNoMoreInteractions(push);
        assertEquals(2, normalLogs.list.size());
        assertFalse(context.getLogger(Logger.ROOT_LOGGER_NAME).isAttached(null));
        assertTrue(context.getLogger(Logger.ROOT_LOGGER_NAME).isAttached(normalLogs));
    }

    @Test
    void requiresBothFcmAndErrorAlertsToBeEnabled() {
        listener.close();
        for (boolean[] settings : new boolean[][]{{false, true}, {true, false}, {false, false}}) {
            listener = new ErrorLogAdminNotificationListener(push, settings[0], settings[1], Duration.ofMinutes(5), 10);
            listener.startListening(context);
            context.getLogger("test.service").error("Disabled listener");
            listener.close();
        }
        verifyNoInteractions(push);
    }

    private void awaitAlerts(int count) {
        verify(push, timeout(3000).times(count)).sendSystemErrorLogAdminNotification(
                anyString(), anyString(), anyString(), any(Instant.class));
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
