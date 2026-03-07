package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ResourceSharingEntity;
import com.nitramite.porssiohjain.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceSharingRepository extends JpaRepository<ResourceSharingEntity, Long> {

    List<ResourceSharingEntity> findBySharerAccountIdAndResourceTypeAndDeviceIdAndEnabledTrue(
            Long sharerAccountId,
            ResourceType resourceType,
            Long deviceId
    );

    List<ResourceSharingEntity> findBySharerAccountIdAndResourceTypeAndControlIdAndEnabledTrue(
            Long sharerAccountId,
            ResourceType resourceType,
            Long controlId
    );

    List<ResourceSharingEntity> findBySharerAccountIdAndResourceTypeAndProductionSourceIdAndEnabledTrue(
            Long sharerAccountId,
            ResourceType resourceType,
            Long productionSourceId
    );

    List<ResourceSharingEntity> findBySharerAccountIdAndResourceTypeAndPowerLimitIdAndEnabledTrue(
            Long sharerAccountId,
            ResourceType resourceType,
            Long powerLimitId
    );

    @Query("""
                SELECT COUNT(r) > 0
                FROM ResourceSharingEntity r
                WHERE r.sharerAccountId = :sharerAccountId
                  AND r.receiverAccountId = :receiverAccountId
                  AND r.resourceType = :type
                  AND (
                        (:type = 'DEVICE' AND r.deviceId = :resourceId) OR
                        (:type = 'CONTROL' AND r.controlId = :resourceId) OR
                        (:type = 'PRODUCTION_SOURCE' AND r.productionSourceId = :resourceId) OR
                        (:type = 'POWER_LIMIT' AND r.powerLimitId = :resourceId)
                      )
            """)
    boolean existsBySharerAccountIdAndReceiverAccountIdAndResourceTypeAndResourceId(
            @Param("sharerAccountId") Long sharerAccountId,
            @Param("receiverAccountId") Long receiverAccountId,
            @Param("type") ResourceType type,
            @Param("resourceId") Long resourceId
    );

    List<ResourceSharingEntity> findByReceiverAccountIdAndResourceTypeAndEnabledTrue(
            Long receiverAccountId,
            ResourceType resourceType
    );

    boolean existsByReceiverAccountIdAndResourceTypeAndControlIdAndEnabledTrue(
            Long receiverAccountId,
            ResourceType resourceType,
            Long controlId
    );

}