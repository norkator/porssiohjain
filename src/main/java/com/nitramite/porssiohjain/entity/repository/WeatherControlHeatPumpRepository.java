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
import com.nitramite.porssiohjain.entity.WeatherControlHeatPumpEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeatherControlHeatPumpRepository extends JpaRepository<WeatherControlHeatPumpEntity, Long> {

    List<WeatherControlHeatPumpEntity> findByDevice(DeviceEntity device);
}
