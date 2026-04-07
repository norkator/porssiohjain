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

import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.services.fmi.FmiWeatherService;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastPointResponse;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteWeatherService {

    private final SiteRepository siteRepository;
    private final SiteWeatherRepository siteWeatherRepository;
    private final FmiWeatherService fmiWeatherService;

    @Value("${site-weather.delete-data-after-days:14}")
    private Integer deleteAfterDays;

    @Transactional
    public void fetchForecastForSite(SiteEntity site) {
        if (site.getWeatherPlace() == null || site.getWeatherPlace().isBlank()) {
            return;
        }
        try {
            SiteWeatherForecastResponse forecast = fmiWeatherService.getForecastForSite(site);
            saveForecast(site, forecast);
            log.info("Fetched and saved {} weather forecast rows for site {}", forecast.getPoints().size(), site.getId());
        } catch (Exception e) {
            log.error("Failed to fetch weather forecast for site {}", site.getId(), e);
        }
    }

    @Transactional
    public int fetchForecastsForConfiguredSites() {
        List<SiteEntity> sites = getSitesWithWeatherPlace();
        int fetchedSites = 0;

        for (SiteEntity site : sites) {
            try {
                SiteWeatherForecastResponse forecast = fmiWeatherService.getForecastForSite(site);
                saveForecast(site, forecast);
                fetchedSites++;
                log.info("Fetched and saved {} weather forecast rows for site {}", forecast.getPoints().size(), site.getId());
            } catch (Exception e) {
                log.error("Failed to fetch weather forecast for site {}", site.getId(), e);
            }
        }

        return fetchedSites;
    }

    @Transactional(readOnly = true)
    public boolean hasWeatherDataForTomorrowForConfiguredSites() {
        List<SiteEntity> sites = getSitesWithWeatherPlace();
        if (sites.isEmpty()) {
            return true;
        }

        Instant start = LocalDate.now(ZoneId.of("Europe/Helsinki")).plusDays(1).atStartOfDay(ZoneId.of("Europe/Helsinki")).toInstant();
        Instant end = LocalDate.now(ZoneId.of("Europe/Helsinki")).plusDays(2).atStartOfDay(ZoneId.of("Europe/Helsinki")).toInstant();

        return sites.stream().allMatch(site -> siteWeatherRepository.existsBySiteAndForecastTimeBetween(site, start, end));
    }

    @Transactional
    public void deleteOldSiteWeatherData() {
        Instant cutoff = LocalDate.now()
                .minusDays(deleteAfterDays)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
        long deleted = siteWeatherRepository.deleteByForecastTimeBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} site weather rows older than {}", deleted, cutoff);
        }
    }

    @Transactional(readOnly = true)
    public SiteWeatherForecastResponse getStoredForecastForSite(SiteEntity site, Instant start, Instant end) {
        List<SiteWeatherEntity> entities = (start != null && end != null)
                ? siteWeatherRepository.findBySiteAndForecastTimeBetweenOrderByForecastTimeAsc(site, start, end)
                : siteWeatherRepository.findBySiteOrderByForecastTimeAsc(site);

        Instant forecastStart = entities.isEmpty() ? null : entities.getFirst().getForecastTime();
        Instant forecastEnd = entities.isEmpty() ? null : entities.getLast().getForecastTime();
        Instant fetchedAt = entities.stream()
                .map(SiteWeatherEntity::getFetchedAt)
                .max(Instant::compareTo)
                .orElse(null);

        return SiteWeatherForecastResponse.builder()
                .siteId(site.getId())
                .siteName(site.getName())
                .weatherPlace(site.getWeatherPlace())
                .fetchedAt(fetchedAt)
                .forecastStartTime(forecastStart)
                .forecastEndTime(forecastEnd)
                .points(entities.stream().map(this::toPointResponse).toList())
                .build();
    }

    private void saveForecast(SiteEntity site, SiteWeatherForecastResponse forecast) {
        if (forecast.getPoints() == null || forecast.getPoints().isEmpty()) {
            return;
        }

        Instant start = forecast.getPoints().getFirst().getTime();
        Instant end = forecast.getPoints().getLast().getTime();

        Map<Instant, SiteWeatherEntity> existingByTime = siteWeatherRepository.findBySiteAndForecastTimeBetween(site, start, end).stream()
                .collect(Collectors.toMap(SiteWeatherEntity::getForecastTime, Function.identity()));

        List<SiteWeatherEntity> entities = forecast.getPoints().stream()
                .map(point -> {
                    SiteWeatherEntity entity = existingByTime.getOrDefault(
                            point.getTime(),
                            SiteWeatherEntity.builder()
                                    .site(site)
                                    .forecastTime(point.getTime())
                                    .build()
                    );
                    entity.setTemperature(point.getTemperature());
                    entity.setWindSpeedMs(point.getWindSpeedMs());
                    entity.setWindGust(point.getWindGust());
                    entity.setHumidity(point.getHumidity());
                    entity.setTotalCloudCover(point.getTotalCloudCover());
                    entity.setPrecipitationAmount(point.getPrecipitationAmount());
                    entity.setFetchedAt(forecast.getFetchedAt() != null ? forecast.getFetchedAt() : Instant.now());
                    return entity;
                })
                .toList();

        siteWeatherRepository.saveAll(entities);
    }

    private List<SiteEntity> getSitesWithWeatherPlace() {
        return siteRepository.findByEnabledTrueAndWeatherPlaceIsNotNull().stream()
                .filter(site -> site.getWeatherPlace() != null && !site.getWeatherPlace().isBlank())
                .toList();
    }

    private SiteWeatherForecastPointResponse toPointResponse(SiteWeatherEntity entity) {
        return SiteWeatherForecastPointResponse.builder()
                .time(entity.getForecastTime())
                .temperature(entity.getTemperature())
                .windSpeedMs(entity.getWindSpeedMs())
                .windGust(entity.getWindGust())
                .humidity(entity.getHumidity())
                .totalCloudCover(entity.getTotalCloudCover())
                .precipitationAmount(entity.getPrecipitationAmount())
                .build();
    }

}
