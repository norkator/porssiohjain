/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.entity.repository.DeviceMeasurementRepository;
import com.nitramite.porssiohjain.entity.repository.TuyaDeviceRepository;
import com.nitramite.porssiohjain.services.tuya.TuyaService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class TuyaScheduler {
    private final TuyaDeviceRepository devices;
    private final TuyaService tuya;
    private final DeviceMeasurementRepository measurements;
    private final AtomicBoolean polling = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("tuya-poll-", 0).factory());

    // Five minutes gives the planner several observations between its 15-minute replans.
    @Scheduled(fixedDelay = 300_000, initialDelay = 15_000)
    public void schedulePolling() {
        if (!polling.compareAndSet(false, true)) return;
        try {
            // Slow cloud requests must not hold up the shared control/notification scheduler.
            worker.execute(() -> {
                try { pollSensors(); }
                finally { polling.set(false); }
            });
        } catch (RejectedExecutionException stopping) {
            polling.set(false);
        }
    }

    @PreDestroy
    public void stop() {
        worker.shutdownNow();
    }

    public void pollSensors() {
        for (var target : devices.findPollTargets()) {
            try {
                // Each device has its own transaction; one failed account does not stop other devices.
                tuya.poll(target.getAccountId(), target.getDeviceId());
            } catch (RuntimeException error) {
                // Provider exceptions and payloads can contain credentials. Log identifiers only.
                log.warn("Tuya poll failed for account {}, device {}", target.getAccountId(), target.getDeviceId());
            }
        }
    }

    @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Helsinki")
    public void deleteOldMeasurements() {
        int deleted = measurements.deleteOlderThan(ZonedDateTime.now(ZoneId.of("Europe/Helsinki"))
                .minusMonths(1).toInstant());
        log.info("Deleted {} old device measurement rows", deleted);
    }
}
