/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ZigbeeGatewayStatusEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZigbeeGatewayStatusRepository extends JpaRepository<ZigbeeGatewayStatusEntity, Long> {
    @EntityGraph(attributePaths = "account")
    @Query("SELECT s FROM ZigbeeGatewayStatusEntity s WHERE (s.offline = false AND s.lastSeen >= :cutoff) OR s.gatewayId IN :clientIds")
    List<ZigbeeGatewayStatusEntity> findConnectionMapGateways(@Param("clientIds") Collection<UUID> clientIds,
                                                           @Param("cutoff") Instant cutoff);

    Optional<ZigbeeGatewayStatusEntity> findByGatewayId(UUID gatewayId);
    List<ZigbeeGatewayStatusEntity> findByOfflineFalseAndLastSeenBefore(Instant cutoff);

    @Query("SELECT MAX(status.lastSeen) FROM ZigbeeGatewayStatusEntity status WHERE status.account.id = :accountId")
    Optional<Instant> findLatestLastSeenByAccountId(@Param("accountId") Long accountId);
}
