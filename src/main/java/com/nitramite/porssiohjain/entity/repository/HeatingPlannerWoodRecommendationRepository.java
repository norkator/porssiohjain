package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.HeatingPlannerWoodRecommendationEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerWoodRecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HeatingPlannerWoodRecommendationRepository
        extends JpaRepository<HeatingPlannerWoodRecommendationEntity, Long> {

    @Query("""
            select recommendation from HeatingPlannerWoodRecommendationEntity recommendation
            where recommendation.account.id = :accountId and recommendation.site.id = :siteId
              and recommendation.settings.id = :settingsId
              and recommendation.status in :statuses
              and recommendation.plan.status <> :supersededStatus
              and recommendation.releaseStartsAt > :now
              and recommendation.releaseStartsAt < :horizonEnd
              and recommendation.notifyAt < :horizonEnd
            order by recommendation.notifyAt, recommendation.id
            """)
    List<HeatingPlannerWoodRecommendationEntity> findUpcomingForWidget(
            @Param("accountId") Long accountId, @Param("siteId") Long siteId,
            @Param("settingsId") Long settingsId,
            @Param("statuses") List<HeatingPlannerWoodRecommendationStatus> statuses,
            @Param("supersededStatus") HeatingPlannerPlanStatus supersededStatus,
            @Param("now") Instant now, @Param("horizonEnd") Instant horizonEnd, Pageable pageable);

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
