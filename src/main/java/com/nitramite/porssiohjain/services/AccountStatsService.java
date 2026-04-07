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
