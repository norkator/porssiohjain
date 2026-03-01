package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ResourceSharingEntity;
import com.nitramite.porssiohjain.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceSharingRepository extends JpaRepository<ResourceSharingEntity, Long> {

    List<ResourceSharingEntity> findBySharerAccountId(Long sharerAccountId);

    List<ResourceSharingEntity> findBySharerAccountIdAndResourceTypeAndEnabledTrue(
            Long sharerAccountId,
            ResourceType resourceType
    );

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

}