package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ZigbeeGatewayBackupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface ZigbeeGatewayBackupRepository extends JpaRepository<ZigbeeGatewayBackupEntity, Long> {
    Optional<ZigbeeGatewayBackupEntity> findByAccountIdAndCoordinatorIeee(Long accountId, String coordinatorIeee);
    Optional<ZigbeeGatewayBackupEntity> findByGatewayId(UUID gatewayId);
    List<ZigbeeGatewayBackupEntity> findByAccountIdOrderByUpdatedAtDesc(Long accountId);
}
