/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ZigbeeGatewayStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZigbeeGatewayStatusRepository extends JpaRepository<ZigbeeGatewayStatusEntity, Long> {
    Optional<ZigbeeGatewayStatusEntity> findByGatewayId(UUID gatewayId);
    List<ZigbeeGatewayStatusEntity> findByOfflineFalseAndLastSeenBefore(Instant cutoff);
}
