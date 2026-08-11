package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerWoodRecommendationEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerWoodRecommendationStatus;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerWoodRecommendationRepository;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.PushNotificationService;
import com.nitramite.porssiohjain.services.PushNotificationTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeatingPlannerWoodNotificationService {
    private final HeatingPlannerWoodRecommendationRepository recommendationRepository;
    private final PushNotificationService pushNotificationService;
    private final PushNotificationTokenService pushNotificationTokenService;
    private final AccountLimitService accountLimitService;

    public void sendDueNotifications() {
        sendDueNotifications(Instant.now());
    }

    @Transactional
    public void sendDueNotifications(Instant now) {
        for (HeatingPlannerWoodRecommendationEntity recommendation : recommendationRepository
                .findByStatusAndNotifyAtLessThanEqualOrderByNotifyAtAsc(
                        HeatingPlannerWoodRecommendationStatus.PENDING, now)) {
            try {
                sendIfDue(recommendation, now);
            } catch (RuntimeException exception) {
                log.error("Failed to process Heating Planner wood recommendation {}",
                        recommendation.getId(), exception);
            }
        }
    }

    private void sendIfDue(HeatingPlannerWoodRecommendationEntity recommendation, Instant now) {
        var settings = recommendation.getSettings();
        if (!settings.isEnabled() || !settings.isStoveLoaded()
                || recommendation.getPlan().getStatus() == HeatingPlannerPlanStatus.SUPERSEDED) {
            recommendation.setStatus(HeatingPlannerWoodRecommendationStatus.SUPERSEDED);
            recommendationRepository.save(recommendation);
            return;
        }
        if (!now.isBefore(recommendation.getReleaseStartsAt())) {
            recommendation.setStatus(HeatingPlannerWoodRecommendationStatus.EXPIRED);
            recommendationRepository.save(recommendation);
            return;
        }

        ZoneId zone = ZoneId.of(settings.getTimezone());
        if (!isInsideActiveWindow(now.atZone(zone).toLocalTime(), settings.getStoveAvailableFrom(),
                settings.getStoveAvailableTo())) {
            return;
        }
        AccountEntity account = recommendation.getAccount();
        if (!account.isPushNotificationsEnabled()
                || !pushNotificationTokenService.hasActivePushToken(account.getId())) {
            return;
        }
        if (!accountLimitService.tryConsumeWeeklyPushNotification(account.getId(), now)) {
            log.info("Wood recommendation {} not sent because account {} reached the weekly push limit",
                    recommendation.getId(), account.getId());
            return;
        }

        Locale locale = account.getLocale() == null || account.getLocale().isBlank()
                ? Locale.ENGLISH : Locale.of(account.getLocale());
        boolean sent = pushNotificationService.sendHeatingPlannerWoodRecommendation(
                account, recommendation, recommendation.getNotifyAt().atZone(zone),
                recommendation.getReleaseStartsAt().atZone(zone), recommendation.getReleaseEndsAt().atZone(zone), locale);
        if (sent) {
            recommendation.setStatus(HeatingPlannerWoodRecommendationStatus.SENT);
            recommendation.setSentAt(now);
            recommendationRepository.save(recommendation);
        }
    }

    private boolean isInsideActiveWindow(LocalTime now, LocalTime from, LocalTime to) {
        if (from == null || to == null || from.equals(to)) return true;
        if (from.isBefore(to)) return !now.isBefore(from) && !now.isAfter(to);
        return !now.isBefore(from) || !now.isAfter(to);
    }
}
