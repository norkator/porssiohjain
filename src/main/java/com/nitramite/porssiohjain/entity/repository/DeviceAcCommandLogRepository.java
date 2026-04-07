/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.DeviceAcCommandLogEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceAcCommandLogRepository extends JpaRepository<DeviceAcCommandLogEntity, Long> {

    List<DeviceAcCommandLogEntity> findTop100ByDeviceOrderBySentAtDescIdDesc(DeviceEntity device);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM device_ac_command_log
            WHERE device_id = :deviceId
              AND id IN (
                  SELECT id
                  FROM device_ac_command_log
                  WHERE device_id = :deviceId
                  ORDER BY sent_at DESC, id DESC
                  OFFSET :maxRows
              )
            """, nativeQuery = true)
    int deleteAllExceptNewest(@Param("deviceId") Long deviceId, @Param("maxRows") int maxRows);
}
