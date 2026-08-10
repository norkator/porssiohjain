/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.services.heating.HeatingPlannerAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class HeatingPlannerAutomationScheduler {
    private final HeatingPlannerAutomationService automationService;

    @Scheduled(cron = "20 2/15 * * * *", zone = "Europe/Helsinki")
    public void recalculateEnabledHeatingPlanners() {
        automationService.runEnabledPlanners(Instant.now());
    }
}
