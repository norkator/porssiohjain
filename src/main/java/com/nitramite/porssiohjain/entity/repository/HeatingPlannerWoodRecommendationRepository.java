package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.HeatingPlannerWoodRecommendationEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerWoodRecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from HeatingPlannerWoodRecommendationEntity recommendation
            where recommendation.plan.id in (
                select plan.id from HeatingPlannerPlanEntity plan
                where plan.horizonEnd < :cutoff
                  and plan.status <> :preservedStatus
            )
            """)
    int deleteByPlanEndedBeforeAndPlanStatusNot(@Param("cutoff") Instant cutoff,
                                                @Param("preservedStatus") HeatingPlannerPlanStatus preservedStatus);
}
