package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanPointRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerRoomRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerSettingsRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerWoodRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeatingPlannerPlanServiceTest {
    @Mock HeatingPlannerSettingsRepository settingsRepository;
    @Mock HeatingPlannerRoomRepository roomRepository;
    @Mock HeatingPlannerPlanRepository planRepository;
    @Mock HeatingPlannerPlanPointRepository pointRepository;
    @Mock HeatingPlannerWoodRecommendationRepository woodRecommendationRepository;

    HeatingPlannerPlanService service;

    @BeforeEach
    void setUp() {
        service = new HeatingPlannerPlanService(settingsRepository, roomRepository, planRepository,
                pointRepository, woodRecommendationRepository);
    }

    @Test
    void cleanupOldPlansDeletesNonActivePlansOlderThanRetention() {
        Instant now = Instant.parse("2026-01-20T12:00:00Z");
        Instant cutoff = Instant.parse("2026-01-06T12:00:00Z");
        when(woodRecommendationRepository.deleteByPlanEndedBeforeAndPlanStatusNot(
                cutoff, HeatingPlannerPlanStatus.ACTIVE)).thenReturn(2);
        when(pointRepository.deleteByPlanEndedBeforeAndPlanStatusNot(
                cutoff, HeatingPlannerPlanStatus.ACTIVE)).thenReturn(96);
        when(planRepository.deleteEndedBeforeAndStatusNot(
                cutoff, HeatingPlannerPlanStatus.ACTIVE)).thenReturn(3);

        HeatingPlannerPlanService.CleanupResult result = service.cleanupOldPlans(now);

        assertThat(result.cutoff()).isEqualTo(cutoff);
        assertThat(result.plansDeleted()).isEqualTo(3);
        assertThat(result.pointsDeleted()).isEqualTo(96);
        assertThat(result.recommendationsDeleted()).isEqualTo(2);
        InOrder inOrder = inOrder(woodRecommendationRepository, pointRepository, planRepository);
        inOrder.verify(woodRecommendationRepository)
                .deleteByPlanEndedBeforeAndPlanStatusNot(cutoff, HeatingPlannerPlanStatus.ACTIVE);
        inOrder.verify(pointRepository).deleteByPlanEndedBeforeAndPlanStatusNot(
                cutoff, HeatingPlannerPlanStatus.ACTIVE);
        inOrder.verify(planRepository).deleteEndedBeforeAndStatusNot(cutoff, HeatingPlannerPlanStatus.ACTIVE);
    }

    @Test
    void heatPumpPreviewUsesSameComfortAndPriceShiftSetpointsAsPersistedPlan() {
        var cheap = point(new BigDecimal("21.00"), HeatingPlanSimulationService.OperatingMode.PREHEAT);
        var expensive = point(new BigDecimal("22.00"), HeatingPlanSimulationService.OperatingMode.DISCHARGE);

        assertThat(HeatingPlannerPlanService.plannedHeatPumpSetpoint(
                new BigDecimal("22.00"), new BigDecimal("20.00"), new BigDecimal("2.00"), true, cheap))
                .isEqualByComparingTo("24.00");
        assertThat(HeatingPlannerPlanService.plannedHeatPumpSetpoint(
                new BigDecimal("22.00"), new BigDecimal("20.00"), new BigDecimal("2.00"), true, expensive))
                .isEqualByComparingTo("20.00");
    }

    private HeatingPlanSimulationService.SimulationPoint point(
            BigDecimal roomTemperature, HeatingPlanSimulationService.OperatingMode mode) {
        return new HeatingPlanSimulationService.SimulationPoint(Instant.parse("2026-01-20T12:00:00Z"),
                BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("23.00"), roomTemperature,
                new BigDecimal("23.00"), BigDecimal.ZERO, false, mode, "test reason");
    }
}
