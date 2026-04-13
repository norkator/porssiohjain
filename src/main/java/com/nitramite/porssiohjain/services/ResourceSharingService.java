package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.ResourceSharingEntity;
import com.nitramite.porssiohjain.entity.enums.ResourceType;
import com.nitramite.porssiohjain.entity.repository.ResourceSharingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceSharingService {

    private final ResourceSharingRepository repository;

    public List<ResourceSharingEntity> getSharesForResource(
            Long sharerAccountId,
            ResourceType type,
            Long resourceId
    ) {
        return switch (type) {
            case DEVICE -> repository
                    .findBySharerAccountIdAndResourceTypeAndDeviceIdAndEnabledTrue(
                            sharerAccountId, type, resourceId);

            case CONTROL -> repository
                    .findBySharerAccountIdAndResourceTypeAndControlIdAndEnabledTrue(
                            sharerAccountId, type, resourceId);

            case PRODUCTION_SOURCE -> repository
                    .findBySharerAccountIdAndResourceTypeAndProductionSourceIdAndEnabledTrue(
                            sharerAccountId, type, resourceId);

            case POWER_LIMIT -> repository
                    .findBySharerAccountIdAndResourceTypeAndPowerLimitIdAndEnabledTrue(
                            sharerAccountId, type, resourceId);

            case WEATHER_CONTROL -> repository
                    .findBySharerAccountIdAndResourceTypeAndWeatherControlIdAndEnabledTrue(
                            sharerAccountId, type, resourceId);
        };
    }

    @Transactional
    public void share(
            Long sharerAccountId,
            Long receiverAccountId,
            ResourceType type,
            Long resourceId
    ) {
        boolean exists = repository.existsBySharerAccountIdAndReceiverAccountIdAndResourceTypeAndResourceId(
                sharerAccountId,
                receiverAccountId,
                type,
                resourceId
        );
        if (exists) {
            return;
        }
        ResourceSharingEntity entity = ResourceSharingEntity.builder()
                .sharerAccountId(sharerAccountId)
                .receiverAccountId(receiverAccountId)
                .resourceType(type)
                .enabled(true)
                .build();
        switch (type) {
            case DEVICE -> entity.setDeviceId(resourceId);
            case CONTROL -> entity.setControlId(resourceId);
            case PRODUCTION_SOURCE -> entity.setProductionSourceId(resourceId);
            case POWER_LIMIT -> entity.setPowerLimitId(resourceId);
            case WEATHER_CONTROL -> entity.setWeatherControlId(resourceId);
        }
        repository.save(entity);
    }

    @Transactional
    public void delete(
            Long sharerAccountId,
            Long shareId
    ) {
        ResourceSharingEntity entity = repository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        if (!entity.getSharerAccountId().equals(sharerAccountId)) {
            throw new IllegalStateException("Not allowed");
        }
        repository.delete(entity);
    }

}
