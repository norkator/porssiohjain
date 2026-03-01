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

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.PowerLimitDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PowerLimitDeviceRepository extends JpaRepository<PowerLimitDeviceEntity, Long> {

    List<PowerLimitDeviceEntity> findByPowerLimitId(Long powerLimitId);

    @Query("select d.device.id from PowerLimitDeviceEntity d where d.powerLimit.id = :powerLimitId")
    List<Long> findDeviceIdsByPowerLimitId(Long powerLimitId);

    List<PowerLimitDeviceEntity> findByDeviceAndDeviceChannel(DeviceEntity device, Integer deviceChannel);

    List<PowerLimitDeviceEntity> findByDevice(DeviceEntity device);

}