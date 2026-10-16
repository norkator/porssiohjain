package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.services.models.SiteWeatherForecastPointResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SiteWeatherForecastChartTest {

    @ParameterizedTest
    @CsvSource({"2026-03-29, Europe/Helsinki, 23", "2026-10-25, Europe/Helsinki, 25",
            "2026-10-16, America/Los_Angeles, 24"})
    void includesWholeLocalDayAndPreservesGapsAcrossDstChanges(LocalDate date, String timezone, int hours) {
        ZoneId zone = ZoneId.of(timezone);
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        var expectedTimes = IntStream.range(0, hours).mapToObj(hour -> start.plusSeconds(hour * 3600L)).toList();
        var forecast = new ArrayList<>(expectedTimes.stream().map(this::point).toList());
        forecast.add(point(start.minusSeconds(1)));
        forecast.add(point(end));
        forecast.add(point(null));
        Collections.reverse(forecast);

        var points = SiteWeatherForecastChart.dayForecast(forecast, date, zone);

        assertThat(points).extracting(SiteWeatherForecastPointResponse::getTime).containsExactlyElementsOf(expectedTimes);
        assertThat(points).allSatisfy(point -> {
            assertThat(point.getTime().atZone(zone).toLocalDate()).isEqualTo(date);
            assertThat(point.getTemperature()).isNull();
            assertThat(point.getWindSpeedMs()).isNull();
            assertThat(point.getHumidity()).isNull();
        });
    }

    private SiteWeatherForecastPointResponse point(Instant time) {
        return SiteWeatherForecastPointResponse.builder().time(time).build();
    }
}
