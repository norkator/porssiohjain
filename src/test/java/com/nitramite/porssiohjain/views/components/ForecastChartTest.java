package com.nitramite.porssiohjain.views.components;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastChartTest {

    @ParameterizedTest
    @CsvSource({"2026-03-29, Europe/Helsinki, 23", "2026-10-25, Europe/Helsinki, 25",
            "2026-11-01, America/New_York, 25", "2026-11-06, Europe/Helsinki, 24"})
    void dayBandsFollowLocalMidnightsAcrossDstAndClipTheFinalPartialDay(LocalDate date, String timezone, int hours) {
        ZoneId zone = ZoneId.of(timezone);
        var start = date.atStartOfDay(zone).toInstant();
        var tomorrow = date.plusDays(1).atStartOfDay(zone).toInstant();
        var end = tomorrow.plusSeconds(15 * 60);

        var bands = ForecastChart.dayBands(start, end, zone);

        assertThat(bands).hasSize(2);
        assertThat(bands.getFirst().date()).isEqualTo(date);
        assertThat(bands.getFirst().start()).isEqualTo(start);
        assertThat(bands.getFirst().end()).isEqualTo(tomorrow);
        assertThat(Duration.between(bands.getFirst().start(), bands.getFirst().end()).toHours()).isEqualTo(hours);
        assertThat(bands.getLast().start()).isEqualTo(tomorrow);
        assertThat(bands.getLast().end()).isEqualTo(end);
        assertThat(ForecastChart.dayBands(start, tomorrow, zone)).hasSize(1);
    }
}
