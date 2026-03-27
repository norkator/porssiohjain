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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.WeatherControlEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlRepository;
import com.nitramite.porssiohjain.services.models.WeatherControlResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WeatherControlService {

    private final WeatherControlRepository weatherControlRepository;
    private final AccountRepository accountRepository;
    private final SiteRepository siteRepository;

    public WeatherControlEntity createWeatherControl(Long accountId, String name, Long siteId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        SiteEntity site = siteRepository.findByIdAndAccountId(siteId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found with id: " + siteId));

        WeatherControlEntity entity = WeatherControlEntity.builder()
                .account(account)
                .name(name)
                .site(site)
                .build();

        return weatherControlRepository.save(entity);
    }

    public List<WeatherControlResponse> getAllWeatherControls(Long accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        return weatherControlRepository.findAllByAccountOrderByIdAsc(account).stream()
                .map(this::toResponse)
                .toList();
    }

    public WeatherControlResponse getWeatherControl(Long accountId, Long weatherControlId) {
        WeatherControlEntity entity = weatherControlRepository.findByIdAndAccountId(weatherControlId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Weather control not found for your account with ID: " + weatherControlId));
        return toResponse(entity);
    }

    private WeatherControlResponse toResponse(WeatherControlEntity entity) {
        return WeatherControlResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .siteId(entity.getSite().getId())
                .siteName(entity.getSite().getName())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

}
