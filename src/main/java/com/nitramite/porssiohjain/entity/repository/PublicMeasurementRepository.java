/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.PublicMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PublicMeasurementRepository extends JpaRepository<PublicMeasurementEntity, Long> {
    @EntityGraph(attributePaths = "device")
    List<PublicMeasurementEntity> findByAccountIdOrderByIdAsc(Long accountId);

    Optional<PublicMeasurementEntity> findByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKey(
            Long accountId, Long deviceId, DeviceMeasurementType type, String key);

    @Query("""
            select p from PublicMeasurementEntity p join fetch p.device d
            where p.account.id = :accountId and d.account.id = :accountId
            order by p.id
            """)
    List<PublicMeasurementEntity> findVisibleForAccount(Long accountId);

    @Query("""
            select p from PublicMeasurementEntity p join fetch p.device d
            where p.id = :id and p.account.id = :accountId and d.account.id = :accountId
            """)
    Optional<PublicMeasurementEntity> findVisibleById(Long accountId, Long id);
}
