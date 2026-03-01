package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ResourceSharingEntity;
import com.nitramite.porssiohjain.entity.ResourceType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ResourceSharingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceSharingService {

    private final ResourceSharingRepository repository;
    private final AccountRepository accountRepository;

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
        };
    }

    @Transactional
    public void updateSharing(
            Long sharerAccountId,
            ResourceType type,
            Long resourceId,
            List<UUID> receiverUuids
    ) {

        List<ResourceSharingEntity> existing =
                getSharesForResource(sharerAccountId, type, resourceId);

        repository.deleteAll(existing);

        for (UUID uuid : receiverUuids) {

            AccountEntity receiver =
                    accountRepository.findByUuid(uuid)
                            .orElseThrow(() -> new IllegalArgumentException("Account not found"));

            ResourceSharingEntity entity = ResourceSharingEntity.builder()
                    .sharerAccountId(sharerAccountId)
                    .receiverAccountId(receiver.getId())
                    .resourceType(type)
                    .enabled(true)
                    .build();

            switch (type) {
                case DEVICE -> entity.setDeviceId(resourceId);
                case CONTROL -> entity.setControlId(resourceId);
                case PRODUCTION_SOURCE -> entity.setProductionSourceId(resourceId);
                case POWER_LIMIT -> entity.setPowerLimitId(resourceId);
            }

            repository.save(entity);
        }
    }

}