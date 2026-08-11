package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerWoodRecommendationStatus;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerWoodRecommendationRepository;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.PushNotificationService;
import com.nitramite.porssiohjain.services.PushNotificationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeatingPlannerWoodNotificationServiceTest {
    @Mock HeatingPlannerWoodRecommendationRepository repository;
    @Mock PushNotificationService pushNotificationService;
    @Mock PushNotificationTokenService tokenService;
    @Mock AccountLimitService accountLimitService;

    private HeatingPlannerWoodNotificationService service;
    private HeatingPlannerWoodRecommendationEntity recommendation;
    private Instant now;

    @BeforeEach
    void setUp() {
        service = new HeatingPlannerWoodNotificationService(repository, pushNotificationService,
                tokenService, accountLimitService);
        now = Instant.parse("2026-01-15T16:15:00Z");
        AccountEntity account = new AccountEntity();
        account.setId(7L);
        account.setLocale("en");
        account.setPushNotificationsEnabled(true);
        SiteEntity site = new SiteEntity(); site.setId(8L);
        HeatingPlannerSettingsEntity settings = HeatingPlannerSettingsEntity.builder().id(1L)
                .account(account).site(site).enabled(true).stoveLoaded(true)
                .timezone("Europe/Helsinki").stoveAvailableFrom(LocalTime.of(6, 0))
                .stoveAvailableTo(LocalTime.of(22, 0)).build();
        HeatingPlannerPlanEntity plan = HeatingPlannerPlanEntity.builder()
                .id(2L).settings(settings).account(account).site(site)
                .status(HeatingPlannerPlanStatus.SIMULATED).build();
        HeatingPlannerRoomEntity room = HeatingPlannerRoomEntity.builder().id(3L).name("Living room")
                .settings(settings).account(account).site(site).build();
        recommendation = HeatingPlannerWoodRecommendationEntity.builder().id(4L).settings(settings).plan(plan)
                .room(room).account(account).site(site).loadName("Configured load")
                .woodAmount(new BigDecimal("8.00")).notifyAt(now.minusSeconds(30))
                .releaseStartsAt(now.plusSeconds(2700)).releaseEndsAt(now.plusSeconds(24300))
                .initialRoomHeatingRate(new BigDecimal("0.35")).reason("Cover expensive period")
                .status(HeatingPlannerWoodRecommendationStatus.PENDING).build();
    }

    @Test
    void sendsDueRecommendationOnceAndMarksItSent() {
        when(repository.findByStatusAndNotifyAtLessThanEqualOrderByNotifyAtAsc(
                HeatingPlannerWoodRecommendationStatus.PENDING, now)).thenReturn(List.of(recommendation));
        when(tokenService.hasActivePushToken(7L)).thenReturn(true);
        when(accountLimitService.tryConsumeWeeklyPushNotification(7L, now)).thenReturn(true);
        when(pushNotificationService.sendHeatingPlannerWoodRecommendation(
                any(), eq(recommendation), any(), any(), any(), any())).thenReturn(true);

        service.sendDueNotifications(now);

        assertThat(recommendation.getStatus()).isEqualTo(HeatingPlannerWoodRecommendationStatus.SENT);
        assertThat(recommendation.getSentAt()).isEqualTo(now);
        verify(repository).save(recommendation);
    }

    @Test
    void doesNotSendWhenStoveIsNoLongerLoaded() {
        recommendation.getSettings().setStoveLoaded(false);
        when(repository.findByStatusAndNotifyAtLessThanEqualOrderByNotifyAtAsc(
                HeatingPlannerWoodRecommendationStatus.PENDING, now)).thenReturn(List.of(recommendation));

        service.sendDueNotifications(now);

        assertThat(recommendation.getStatus()).isEqualTo(HeatingPlannerWoodRecommendationStatus.SUPERSEDED);
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void expiresRecommendationAfterUsefulHeatShouldHaveStarted() {
        Instant late = recommendation.getReleaseStartsAt();
        when(repository.findByStatusAndNotifyAtLessThanEqualOrderByNotifyAtAsc(
                HeatingPlannerWoodRecommendationStatus.PENDING, late)).thenReturn(List.of(recommendation));

        service.sendDueNotifications(late);

        assertThat(recommendation.getStatus()).isEqualTo(HeatingPlannerWoodRecommendationStatus.EXPIRED);
        verifyNoInteractions(pushNotificationService);
    }
}
