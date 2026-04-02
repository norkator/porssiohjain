/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
