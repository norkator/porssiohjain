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

import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SiteWeatherRepository extends JpaRepository<SiteWeatherEntity, Long> {

    List<SiteWeatherEntity> findBySiteAndForecastTimeBetween(SiteEntity site, Instant start, Instant end);

    List<SiteWeatherEntity> findBySiteOrderByForecastTimeAsc(SiteEntity site);

    List<SiteWeatherEntity> findBySiteAndForecastTimeBetweenOrderByForecastTimeAsc(SiteEntity site, Instant start, Instant end);

    Optional<SiteWeatherEntity> findFirstBySiteAndForecastTimeLessThanEqualOrderByForecastTimeDesc(
            SiteEntity site,
            Instant forecastTime
    );

    Optional<SiteWeatherEntity> findFirstBySiteAndForecastTimeGreaterThanEqualOrderByForecastTimeAsc(
            SiteEntity site,
            Instant forecastTime
    );

    boolean existsBySiteAndForecastTimeBetween(SiteEntity site, Instant start, Instant end);

    long deleteByForecastTimeBefore(Instant cutoff);

}
