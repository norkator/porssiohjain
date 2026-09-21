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

package com.nitramite.porssiohjain.services.fmi;

import com.nitramite.porssiohjain.entity.SiteEntity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FmiWeatherServiceTest {

    @ParameterizedTest
    @CsvSource({
            "Seinäjoki, Sein%C3%A4joki",
            "Tampere, Tampere"
    })
    void encodesWeatherPlaceInForecastUri(String weatherPlace, String expectedEncodedPlace) {
        FmiWeatherService service = new FmiWeatherService();
        ReflectionTestUtils.setField(service, "wfsUrl", "https://opendata.fmi.fi/wfs");
        ReflectionTestUtils.setField(service, "storedQueryId", "fmi::forecast::harmonie::surface::point::timevaluepair");
        ReflectionTestUtils.setField(service, "timestepMinutes", 60);
        ReflectionTestUtils.setField(service, "forecastParameters", "temperature,windspeedms");
        SiteEntity site = SiteEntity.builder().weatherPlace(weatherPlace).build();

        URI uri = service.buildForecastUri(
                site,
                Instant.parse("2026-09-21T10:00:00Z"),
                Instant.parse("2026-09-22T10:00:00Z")
        );

        assertEquals(expectedEncodedPlace, uri.getRawQuery().lines()
                .flatMap(query -> java.util.Arrays.stream(query.split("&")))
                .filter(parameter -> parameter.startsWith("place="))
                .map(parameter -> parameter.substring("place=".length()))
                .findFirst()
                .orElseThrow());
    }
}
