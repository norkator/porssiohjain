package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.services.WaterMeterNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class WaterMeterNotificationScheduler {
    private final WaterMeterNotificationService notifications;

    @Scheduled(cron = "45 * * * * *", zone = "Europe/Helsinki")
    public void sendDueNotifications() {
        Instant now = Instant.now();
        for (var meter : notifications.enabledForNotifications()) {
            try {
                notifications.sendIfDue(meter.accountId(), meter.id(), now);
            } catch (RuntimeException exception) {
                log.warn("Water meter notification could not complete: meterId={}", meter.id());
            }
        }
    }
}
