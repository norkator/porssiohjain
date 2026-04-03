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

import com.nitramite.porssiohjain.entity.repository.PowerLimitRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceRepository;
import com.nitramite.porssiohjain.services.models.AccountPowerLimitStatResponse;
import com.nitramite.porssiohjain.services.models.AccountProductionSourceStatResponse;
import com.nitramite.porssiohjain.services.models.AccountStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountStatsService {

    private final PowerLimitRepository powerLimitRepository;
    private final ProductionSourceRepository productionSourceRepository;

    @Transactional(readOnly = true)
    public AccountStatsResponse getStats(Long accountId) {
        return AccountStatsResponse.builder()
                .powerLimits(powerLimitRepository.findByAccountId(accountId).stream()
                        .map(entity -> AccountPowerLimitStatResponse.builder()
                                .name(entity.getName())
                                .currentKw(entity.getCurrentKw())
                                .build())
                        .toList())
                .productionSources(productionSourceRepository.findByAccountId(accountId).stream()
                        .map(entity -> AccountProductionSourceStatResponse.builder()
                                .name(entity.getName())
                                .currentKw(entity.getCurrentKw())
                                .peakKw(entity.getPeakKw())
                                .build())
                        .toList())
                .build();
    }
}
