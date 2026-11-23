package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.WaterMeasurementEntity;
import com.nitramite.porssiohjain.entity.WaterMeterEntity;
import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import com.nitramite.porssiohjain.entity.repository.WaterMeasurementRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaterMeterEstimationTest {
    private final WaterMeasurementRepository measurements = mock();
    private final Instant now = Instant.parse("2026-11-23T10:00:00Z"); // Helsinki noon
    private final WaterMeterEntity meter = meter(LocalDate.of(2026, 11, 21));

    static WaterMeterEntity meter(LocalDate through) {
        var meter = new WaterMeterEntity();
        meter.setId(12L);
        meter.setProvider(WaterMeterProvider.TAMPEREEN_VESI);
        meter.setBaselineDate(through.minusDays(30));
        meter.setConsumptionThrough(through);
        meter.setLatestTotalCubicMeters(new BigDecimal("100"));
        meter.setObservedAt(through.plusDays(1).atStartOfDay(WaterMeterEstimation.ZONE).toInstant());
        return meter;
    }

    static WaterMeasurementEntity row(LocalDate date, String usage) {
        var row = new WaterMeasurementEntity();
        row.setConsumptionDate(date);
        row.setConsumptionCubicMeters(usage == null ? null : new BigDecimal(usage));
        return row;
    }

    private void readings(WaterMeterEntity selected, List<WaterMeasurementEntity> rows) {
        when(measurements.findByWaterMeterIdAndWaterMeterAccountIdAndConsumptionDateBetweenOrderByConsumptionDateAsc(
                eq(12L), eq(7L), any(), any())).thenReturn(rows);
    }

    @Test void averagesReportedDaysAndProjectsToNowWithoutChangingTheSnapshot() {
        readings(meter, List.of(row(meter.getConsumptionThrough(), "0.6"), row(meter.getConsumptionThrough().minusDays(1), "0.4")));
        var result = WaterMeterEstimation.estimate(meter, 7L, measurements, now);
        assertThat(result.total().value()).isEqualByComparingTo("100.75");
        assertThat(result.total().averageDailyCubicMeters()).isEqualByComparingTo("0.5");
        assertThat(result.total().basedOnDays()).isEqualTo(2);
        assertThat(result.daily().value()).isEqualByComparingTo("0.25");
        assertThat(result.totalPoints()).hasSize(3);
        assertThat(result.totalPoints().getLast().at()).isEqualTo(now);
        assertThat(meter.getLatestTotalCubicMeters()).isEqualByComparingTo("100");
        verify(measurements, never()).save(any());
    }

    @Test void usesAtMostSevenConsecutiveDaysIncludingRealZeroUsage() {
        var rows = new ArrayList<WaterMeasurementEntity>();
        for (int i = 0; i < 8; i++) rows.add(row(meter.getConsumptionThrough().minusDays(i), i == 7 ? "100" : "0"));
        readings(meter, rows);
        var result = WaterMeterEstimation.estimate(meter, 7L, measurements, now);
        assertThat(result.total().basedOnDays()).isEqualTo(7);
        assertThat(result.total().value()).isEqualByComparingTo("100");
    }

    @Test void missingOrInvalidRecentUsageDoesNotBecomeZeroOrUseOlderDisconnectedHistory() {
        var through = meter.getConsumptionThrough();
        for (var rows : List.of(List.of(row(through, "0.5")),
                List.of(row(through, "0.5"), row(through.minusDays(2), "10")),
                List.of(row(through, "0.5"), row(through.minusDays(1), null)),
                List.of(row(through, "0.5"), row(through.minusDays(1), "-1")))) {
            readings(meter, rows);
            assertThat(WaterMeterEstimation.estimate(meter, 7L, measurements, now)).isNull();
        }
    }

    @Test void suppressesEstimatesAcrossAnInternalGapWithKnownLaterUsage() {
        var through = meter.getConsumptionThrough();
        readings(meter, List.of(row(through, "0.6"), row(through.minusDays(1), "0.4"), row(through.plusDays(2), "1.2")));
        assertThat(WaterMeterEstimation.estimate(meter, 7L, measurements, now)).isNull();
    }

    @Test void disabledUninitializedOldFutureAndOtherProviderSourcesAreNotProjected() {
        var stale = meter(LocalDate.of(2026, 11, 10));
        var future = meter(LocalDate.of(2026, 11, 24));
        var wrm = meter(meter.getConsumptionThrough()); wrm.setProvider(WaterMeterProvider.WRM_SYSTEMS_WMD);
        var disabled = meter(meter.getConsumptionThrough()); disabled.setEnabled(false);
        var unset = meter(meter.getConsumptionThrough()); unset.setConsumptionThrough(null);
        for (var selected : List.of(stale, future, wrm, disabled, unset))
            assertThat(WaterMeterEstimation.estimate(selected, 7L, measurements, now)).isNull();
        verifyNoInteractions(measurements);
    }

    @Test void sourceChangesCannotReuseHistoryBeforeTheNewBaseline() {
        meter.setBaselineDate(meter.getConsumptionThrough());
        readings(meter, List.of(row(meter.getConsumptionThrough(), "0.6"), row(meter.getConsumptionThrough().minusDays(1), "0.4")));
        assertThat(WaterMeterEstimation.estimate(meter, 7L, measurements, now)).isNull();
    }

    @Test void calendarDayAverageProratesBothDstTransitions() {
        for (var test : List.of(new Object[]{LocalDate.of(2026, 10, 23), "2026-10-25T10:00:00Z", "100.76"},
                new Object[]{LocalDate.of(2026, 3, 27), "2026-03-29T09:00:00Z", "100.739130"})) {
            var selected = meter((LocalDate) test[0]);
            readings(selected, List.of(row(selected.getConsumptionThrough(), "0.5"), row(selected.getConsumptionThrough().minusDays(1), "0.5")));
            var result = WaterMeterEstimation.estimate(selected, 7L, measurements, Instant.parse((String) test[1]));
            assertThat(result.total().value()).isEqualByComparingTo((String) test[2]);
        }
    }
}
