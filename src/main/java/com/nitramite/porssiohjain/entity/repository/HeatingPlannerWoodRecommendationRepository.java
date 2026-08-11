package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.HeatingPlannerWoodRecommendationEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerWoodRecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HeatingPlannerWoodRecommendationRepository
        extends JpaRepository<HeatingPlannerWoodRecommendationEntity, Long> {

    Optional<HeatingPlannerWoodRecommendationEntity> findBySettingsIdAndReleaseStartsAt(
            Long settingsId, Instant releaseStartsAt);

    List<HeatingPlannerWoodRecommendationEntity> findByPlanIdAndStatus(
            Long planId, HeatingPlannerWoodRecommendationStatus status);

    List<HeatingPlannerWoodRecommendationEntity> findByStatusAndNotifyAtLessThanEqualOrderByNotifyAtAsc(
            HeatingPlannerWoodRecommendationStatus status, Instant now);
}
