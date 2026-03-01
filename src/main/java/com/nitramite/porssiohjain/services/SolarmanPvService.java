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

import com.nitramite.porssiohjain.entity.ProductionApiType;
import com.nitramite.porssiohjain.entity.ProductionHistoryEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import com.nitramite.porssiohjain.entity.repository.ProductionHistoryRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceRepository;
import com.nitramite.porssiohjain.services.models.SolarmanStationResponse;
import com.nitramite.porssiohjain.services.models.SolarmanTokenCache;
import com.nitramite.porssiohjain.services.models.SolarmanTokenResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SolarmanPvService {

    private final RestTemplate restTemplate = new RestTemplate();

    private final ProductionSourceRepository productionSourceRepository;
    private final ProductionHistoryRepository productionHistoryRepository;
    private final SystemLogService systemLogService;

    private final Map<Long, SolarmanTokenCache> tokenCache = new ConcurrentHashMap<>();
    private static final long MAX_DATA_AGE_SECONDS = 1800;

    private final String baseUrl = "https://globalapi.solarmanpv.com";

    public SolarmanPvService(
            ProductionSourceRepository productionSourceRepository,
            SystemLogService systemLogService,
            ProductionHistoryRepository productionHistoryRepository
    ) {
        this.productionSourceRepository = productionSourceRepository;
        this.systemLogService = systemLogService;
        this.productionHistoryRepository = productionHistoryRepository;
    }

    @Transactional
    public void fetchGenerationData() {
        List<ProductionSourceEntity> sources = productionSourceRepository
                .findByEnabledTrueAndApiType(ProductionApiType.SOFAR_SOLARMANPV);
        for (ProductionSourceEntity source : sources) {
            try {
                Double kwDouble = fetchCurrentKw(source);
                BigDecimal kw = BigDecimal.valueOf(kwDouble).setScale(2, RoundingMode.HALF_UP);
                source.setCurrentKw(kw);
                if (source.getPeakKw() == null || kw.compareTo(source.getPeakKw()) > 0) {
                    source.setPeakKw(kw);
                }
                productionSourceRepository.save(source);
                ProductionHistoryEntity history = ProductionHistoryEntity.builder()
                        .account(source.getAccount())
                        .productionSource(source)
                        .kilowatts(kw)
                        .build();
                productionHistoryRepository.save(history);
                log.info("Solarman source {} production {} kW", source.getId(), kw);
            } catch (Exception e) {
                log.error("Solarman fetch failed for source {}", source.getId(), e);
                systemLogService.log(
                        "Solarman fetch error for source " + source.getId() + ": " + e.getMessage()
                );
            }
        }
    }

    private Double fetchCurrentKw(
            ProductionSourceEntity source
    ) {
        String token = getValidToken(source);
        return fetchStationPower(source, token);
    }

    private String getValidToken(ProductionSourceEntity source) {
        SolarmanTokenCache cache = tokenCache.get(source.getId());
        if (cache != null && cache.getExpiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return cache.getToken();
        }
        return requestNewToken(source);
    }

    private String requestNewToken(ProductionSourceEntity source) {
        String url = baseUrl + "/account/v1.0/token?appId=" + source.getAppId();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("email", source.getEmail());
        body.put("password", source.getPassword());
        body.put("appSecret", source.getAppSecret());

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<SolarmanTokenResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                SolarmanTokenResponse.class
        );

        SolarmanTokenResponse tokenResponse = response.getBody();
        if (tokenResponse == null || tokenResponse.getAccess_token() == null) {
            throw new IllegalStateException("Solarman token response invalid");
        }

        Instant expiresAt = Instant.now().plusSeconds(tokenResponse.getExpires_in());
        tokenCache.put(source.getId(), new SolarmanTokenCache(tokenResponse.getAccess_token(), expiresAt));

        systemLogService.log("Solarman token refreshed for source " + source.getId());
        return tokenResponse.getAccess_token();
    }

    private Double fetchStationPower(ProductionSourceEntity source, String token) {
        String url = baseUrl + "/station/v1.0/realTime";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, String> body = Map.of("stationId", source.getStationId());

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<SolarmanStationResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                SolarmanStationResponse.class
        );

        SolarmanStationResponse data = response.getBody();
        if (data == null) {
            throw new IllegalStateException("Solarman station response null");
        }

        Long lastUpdateTime = data.getLastUpdateTime();
        if (lastUpdateTime == null) {
            throw new IllegalStateException("Solarman lastUpdateTime missing");
        }

        long now = Instant.now().getEpochSecond();
        long age = now - lastUpdateTime;

        if (age > MAX_DATA_AGE_SECONDS) {
            log.warn("Solarman data stale ({}s old) for station {}", age, source.getStationId());
            return 0d;
        }

        double watts = data.getGenerationPower() != null ? data.getGenerationPower() : 0d;
        return watts / 1000.0;
    }

}
