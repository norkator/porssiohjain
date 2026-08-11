/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.services.heating.HeatingPlannerAutomationService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerPlanService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerWoodNotificationService;
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
public class HeatingPlannerAutomationScheduler {
    private final HeatingPlannerAutomationService automationService;
    private final HeatingPlannerPlanService planService;
    private final HeatingPlannerWoodNotificationService woodNotificationService;

    @Scheduled(cron = "20 2/15 * * * *", zone = "Europe/Helsinki")
    public void recalculateEnabledHeatingPlanners() {
        automationService.runEnabledPlanners(Instant.now());
    }

    @Scheduled(cron = "10 0/1 * * * *", zone = "Europe/Helsinki")
    public void sendDueWoodRecommendations() {
        woodNotificationService.sendDueNotifications();
    }

    @Scheduled(cron = "0 37 3 * * *", zone = "Europe/Helsinki")
    public void cleanupOldHeatingPlannerPlans() {
        HeatingPlannerPlanService.CleanupResult result = planService.cleanupOldPlans(Instant.now());
        if (result.plansDeleted() > 0 || result.pointsDeleted() > 0 || result.recommendationsDeleted() > 0) {
            log.info("Deleted old Heating Planner rows before {}: plans={}, points={}, woodRecommendations={}",
                    result.cutoff(), result.plansDeleted(), result.pointsDeleted(), result.recommendationsDeleted());
        }
    }
}
