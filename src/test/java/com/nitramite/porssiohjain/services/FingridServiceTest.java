package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.FingridDataEntity;
import com.nitramite.porssiohjain.entity.repository.FingridDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FingridServiceTest {
    @Mock private FingridDataRepository repository;

    @Test
    void includesTheWholeStoredHorizonAndKeepsDailyAveragesOnTheLocalDstDay() {
        List<FingridDataEntity> rows = List.of(
                point("2026-10-24T21:00:00Z", "1000"),
                point("2026-10-25T21:45:00Z", "3000"),
                point("2026-10-25T22:00:00Z", "500"),
                point("2026-10-26T21:45:00Z", "1500"),
                point("2026-10-26T22:00:00Z", "10000"),
                point("2026-10-29T22:00:00Z", "7000")
        );
        when(repository.findByDatasetIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                245, Instant.parse("2026-10-24T21:00:00Z"))).thenReturn(rows);

        var chart = new FingridService(repository).getWindForecastChart(
                "Europe/Helsinki", Instant.parse("2026-10-25T12:00:00Z"));

        assertEquals("Europe/Helsinki", chart.getTimezone());
        assertEquals(new BigDecimal("2000.00"), chart.getTodayAverage());
        assertEquals(new BigDecimal("1000.00"), chart.getTomorrowAverage());
        assertEquals(new BigDecimal("50.00"), chart.getTomorrowDropPercent());
        assertEquals(rows.stream().map(FingridDataEntity::getStartTime).toList(),
                chart.getPoints().stream().map(point -> point.getStartTime()).toList());
        assertEquals(Instant.parse("2026-10-29T22:15:00Z"), chart.getPoints().getLast().getEndTime());
        assertEquals(new BigDecimal("7000"), chart.getPoints().getLast().getMegawatts());
        verify(repository).findByDatasetIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                245, Instant.parse("2026-10-24T21:00:00Z"));
    }

    @Test
    void emptyForecastUsesTheLocalDateEvenWhenUtcIsStillOnThePreviousDay() {
        when(repository.findByDatasetIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                245, Instant.parse("2026-08-22T21:00:00Z"))).thenReturn(List.of());

        var chart = new FingridService(repository).getWindForecastChart(
                "Europe/Helsinki", Instant.parse("2026-08-22T21:30:00Z"));

        assertTrue(chart.getPoints().isEmpty());
        assertNull(chart.getTodayAverage());
        assertNull(chart.getTomorrowAverage());
        assertNull(chart.getTomorrowDropPercent());
        verify(repository).findByDatasetIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                245, Instant.parse("2026-08-22T21:00:00Z"));
    }

    @Test
    void laterForecastDaysDoNotFillInMissingTodayAndTomorrowAverages() {
        when(repository.findByDatasetIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                245, Instant.parse("2026-01-01T00:00:00Z")))
                .thenReturn(List.of(point("2026-01-03T00:00:00Z", "6000")));

        var chart = new FingridService(repository).getWindForecastChart("UTC", Instant.parse("2026-01-01T12:00:00Z"));

        assertEquals(1, chart.getPoints().size());
        assertNull(chart.getTodayAverage());
        assertNull(chart.getTomorrowAverage());
        assertNull(chart.getTomorrowDropPercent());
    }

    private FingridDataEntity point(String timestamp, String value) {
        var row = new FingridDataEntity();
        row.setDatasetId(245);
        row.setStartTime(Instant.parse(timestamp));
        row.setEndTime(row.getStartTime().plus(15, ChronoUnit.MINUTES));
        row.setValue(new BigDecimal(value));
        return row;
    }
}
