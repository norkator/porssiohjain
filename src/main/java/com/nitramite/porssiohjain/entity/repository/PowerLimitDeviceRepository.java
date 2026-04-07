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