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

import com.nitramite.porssiohjain.entity.repository.PricePredictionRepository;
import com.nitramite.porssiohjain.services.models.PricePredictionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PricePredictionService {

    private final PricePredictionRepository predictionRepository;

    @Transactional(readOnly = true)
    public List<PricePredictionResponse> getPredictions(
            Instant startDate,
            Instant endDate
    ) {
        Instant now = Instant.now();
        Instant start = startDate != null ? startDate : now.truncatedTo(ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : start.plus(2, ChronoUnit.DAYS).minusNanos(1);

        return predictionRepository.findBetween(start, end).stream()
                .map(p -> PricePredictionResponse.builder()
                        .timestamp(p.getTimestamp())
                        .priceCents(p.getPriceCents())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PricePredictionResponse> getFuturePredictions() {
        Instant now = Instant.now();

        return predictionRepository.findByTimestampAfterOrderByTimestampAsc(now).stream()
                .map(p -> PricePredictionResponse.builder()
                        .timestamp(p.getTimestamp())
                        .priceCents(p.getPriceCents())
                        .build())
                .toList();
    }

}
