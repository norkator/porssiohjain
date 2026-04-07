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
import com.nitramite.porssiohjain.entity.ProductionSourceDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionSourceDeviceRepository extends JpaRepository<ProductionSourceDeviceEntity, Long> {

    List<ProductionSourceDeviceEntity> findByProductionSourceId(Long productionSourceId);

    void deleteByIdAndProductionSourceId(Long id, Long productionSourceId);

    List<ProductionSourceDeviceEntity> findAllByDevice(DeviceEntity device);

    List<ProductionSourceDeviceEntity> findByDevice(DeviceEntity device);

}