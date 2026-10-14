package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class HeatingPlanChartTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/Helsinki");

    @Test
    void retainsWholeDayPricesWithoutRecreatingElapsedTemperaturesFromCurrentReading() {
        LocalDate date = LocalDate.of(2026, 10, 14);
        Instant start = date.atStartOfDay(ZONE).toInstant();
        Instant current = start.plusSeconds(20 * 3600 + 30 * 60);
        List<HeatingPlanSimulationService.MarketPoint> market = IntStream.range(0, 192)
                .mapToObj(index -> new HeatingPlanSimulationService.MarketPoint(start.plusSeconds(index * 900L),
                        BigDecimal.valueOf(index), BigDecimal.ZERO, BigDecimal.ZERO)).toList();

        var points = HeatingPlanChart.dayPoints(market, List.of(prediction(current, "22.82")),
                List.of(), date, ZONE, current.plusSeconds(60));

        assertThat(points).hasSize(96);
        assertThat(points.getFirst().time()).isEqualTo(start);
        assertThat(points.getLast().time()).isEqualTo(date.atTime(23, 45).atZone(ZONE).toInstant());
        assertThat(points).extracting(HeatingPlanChart.ChartPoint::price)
                .containsExactlyElementsOf(IntStream.range(0, 96).mapToObj(index -> (double) index).toList());
        assertThat(points.subList(0, 82)).allSatisfy(point -> {
            assertThat(point.room()).isNull();
            assertThat(point.floor()).isNull();
            assertThat(point.setpoint()).isNull();
            assertThat(point.wood()).isNull();
        });
        assertThat(points.get(82).room()).isEqualTo(22.82);
    }

    @Test
    void usesSavedPredictionsOnlyForCompletedPeriodsAndKeepsCurrentPredictionsAuthoritative() {
        LocalDate date = LocalDate.of(2026, 10, 14);
        Instant start = date.atStartOfDay(ZONE).toInstant();
        Instant now = start.plusSeconds(10 * 3600 + 5 * 60);
        var history = List.of(prediction(start, "20.00"), prediction(start.plusSeconds(10 * 3600), "19.00"),
                prediction(start.plusSeconds(11 * 3600), "18.00"));
        var live = List.of(prediction(start.plusSeconds(10 * 3600), "22.82"));

        var points = HeatingPlanChart.dayPoints(List.of(), live, history, date, ZONE, now);

        assertThat(points.getFirst().room()).isEqualTo(20.0);
        assertThat(points.getFirst().price()).isNull();
        assertThat(points.get(1).room()).isNull();
        assertThat(points.get(40).room()).isEqualTo(22.82);
        assertThat(points.get(44).room()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"2026-03-29, Europe/Helsinki, 92", "2026-10-25, Europe/Helsinki, 100",
            "2026-10-14, America/Los_Angeles, 96"})
    void usesWholeLocalCalendarDayIncludingDstChanges(LocalDate date, String timezone, int expectedPoints) {
        ZoneId zone = ZoneId.of(timezone);
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();

        var points = HeatingPlanChart.dayPoints(List.of(), List.of(), List.of(), date, zone, start);

        assertThat(points).hasSize(expectedPoints);
        assertThat(points.getFirst().time()).isEqualTo(start);
        assertThat(points.getLast().time()).isEqualTo(end.minusSeconds(900));
        assertThat(points).extracting(HeatingPlanChart.ChartPoint::time).doesNotHaveDuplicates();
        assertThat(points).allSatisfy(point -> assertThat(point.time().atZone(zone).toLocalDate()).isEqualTo(date));
    }

    private HeatingPlanSimulationService.SimulationPoint prediction(Instant time, String room) {
        return new HeatingPlanSimulationService.SimulationPoint(time, BigDecimal.TEN, BigDecimal.ZERO,
                new BigDecimal("23.00"), new BigDecimal(room), new BigDecimal("23.00"), BigDecimal.ZERO,
                false, HeatingPlanSimulationService.OperatingMode.NORMAL, "Test prediction");
    }
}
