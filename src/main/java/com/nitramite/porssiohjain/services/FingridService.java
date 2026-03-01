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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.FingridDataEntity;
import com.nitramite.porssiohjain.entity.repository.FingridDataRepository;
import com.nitramite.porssiohjain.services.models.FingridWindForecastResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FingridService {

    private final FingridDataRepository fingridDataRepository;

    public List<FingridWindForecastResponse> getFingridWindForecastData() {
        Instant now = Instant.now();

        List<FingridDataEntity> forecastEntities = fingridDataRepository.findByDatasetIdAndStartTimeAfter(245, now);

        return forecastEntities.stream()
                .map(n -> FingridWindForecastResponse.builder()
                        .startTime(n.getStartTime())
                        .endTime(n.getEndTime())
                        .value(n.getValue())
                        .build())
                .toList();
    }

}
