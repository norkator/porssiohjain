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
