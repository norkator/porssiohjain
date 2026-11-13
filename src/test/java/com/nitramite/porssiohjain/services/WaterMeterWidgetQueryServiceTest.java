package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import com.nitramite.porssiohjain.entity.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaterMeterWidgetQueryServiceTest {
    private final WaterMeterRepository meters = mock();
    private final WaterMeasurementRepository measurements = mock();
    private final WaterMeterWidgetQueryService service = new WaterMeterWidgetQueryService(meters, measurements);
    private final Instant now = Instant.parse("2026-10-26T12:00:00Z");

    private WaterMeterEntity meter() {
        var meter = new WaterMeterEntity();
        meter.setId(12L);
        meter.setName("Home water");
        meter.setProvider(WaterMeterProvider.TAMPEREEN_VESI);
        meter.setLatestTotalCubicMeters(new BigDecimal("123.456"));
        meter.setObservedAt(now);
        meter.setConsumptionThrough(LocalDate.of(2026,10,25));
        meter.setUsername("private-user");
        meter.setPassword("private-password");
        return meter;
    }
    private WaterMeasurementEntity daily(LocalDate day) {
        var row = new WaterMeasurementEntity();
        row.setConsumptionDate(day);
        row.setConsumptionCubicMeters(new BigDecimal("0.5"));
        row.setTotalCubicMeters(new BigDecimal("123.456"));
        row.setObservedAt(now);
        return row;
    }
    @Test void listsTotalsAndDailyUsageWithoutCredentialsOrInventedMeasurementTimes() {
        when(meters.findByAccountIdOrderByNameAscIdAsc(7L)).thenReturn(List.of(meter()));
        when(measurements.findFirstByWaterMeterIdAndWaterMeterAccountIdAndConsumptionDateIsNotNullAndConsumptionCubicMetersIsNotNullOrderByConsumptionDateDescIdDesc(12L,7L))
                .thenReturn(Optional.of(daily(LocalDate.of(2026,10,25))));
        var result = service.latest(7L);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().measurementType()).isEqualTo(WaterMeterWidgetQueryService.Type.WATER_METER_TOTAL);
        assertThat(result.getFirst().calculated()).isTrue();
        assertThat(result.getFirst().unit()).isEqualTo("m³");
        assertThat(result.getFirst().measuredAt()).isNull();
        assertThat(result.getLast().value()).isEqualByComparingTo("0.5");
        assertThat(result.toString()).doesNotContain("private");
    }
    @Test void wrmHasOnlyATotalAndUnpolledMeterHasNoInventedZero() {
        var wrm = meter(); wrm.setProvider(WaterMeterProvider.WRM_SYSTEMS_WMD);
        var empty = meter(); empty.setId(13L); empty.setLatestTotalCubicMeters(null); empty.setObservedAt(null);
        when(meters.findByAccountIdOrderByNameAscIdAsc(7L)).thenReturn(List.of(wrm, empty));
        var result = service.latest(7L);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().calculated()).isFalse();
    }
    @Test void historyUsesConsumptionDayAcrossDstAndKeepsTheHalfOpenWindow() {
        when(meters.findByIdAndAccountId(12L,7L)).thenReturn(Optional.of(meter()));
        var row = daily(LocalDate.of(2026,10,25)); // 25-hour Helsinki day; ends at 22:00 UTC.
        var upperBoundary = daily(LocalDate.of(2026,10,26));
        upperBoundary.setConsumptionDate(null); upperBoundary.setObservedAt(now);
        var lowerBoundary = daily(LocalDate.of(2026,10,23));
        lowerBoundary.setConsumptionDate(null); lowerBoundary.setObservedAt(now.minusSeconds(48*3600));
        when(measurements.findWidgetHistory(eq(12L),eq(7L),any(),any(),any(),any()))
                .thenReturn(List.of(upperBoundary,row,lowerBoundary));
        var result = service.history(7L,12L,WaterMeterWidgetQueryService.Type.WATER_METER_TOTAL,"total_m3",48,now);
        assertThat(result.points()).hasSize(2);
        assertThat(result.points().getFirst().observedAt()).isEqualTo(result.from());
        assertThat(result.points().getLast().observedAt()).isEqualTo(Instant.parse("2026-10-25T22:00:00Z"));
        assertThat(Duration.between(result.from(),result.to())).isEqualTo(Duration.ofHours(48));
        verify(measurements).findWidgetHistory(12L,7L,result.from(),now,LocalDate.of(2026,10,23),LocalDate.of(2026,10,27));
    }
    @Test void dailyHistoryIncludesUsagePendingACumulativeTotal() {
        when(meters.findByIdAndAccountId(12L,7L)).thenReturn(Optional.of(meter()));
        var row = daily(LocalDate.of(2026,10,25)); row.setTotalCubicMeters(null);
        when(measurements.findWidgetHistory(eq(12L),eq(7L),any(),any(),any(),any())).thenReturn(List.of(row));
        assertThat(service.history(7L,12L,WaterMeterWidgetQueryService.Type.WATER_CONSUMPTION,"daily_m3",24,now).points())
                .hasSize(1);
        assertThat(service.history(7L,12L,WaterMeterWidgetQueryService.Type.WATER_METER_TOTAL,"total_m3",24,now).points())
                .isEmpty();
    }
    @Test void unownedOrDeletedMetersAndInvalidSelectionsFailBeforeHistoryQueries() {
        assertThatThrownBy(() -> service.history(99L,12L,WaterMeterWidgetQueryService.Type.WATER_METER_TOTAL,"total_m3",24,now))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("410");
        assertThatThrownBy(() -> service.history(7L,12L,WaterMeterWidgetQueryService.Type.WATER_METER_TOTAL,"daily_m3",24,now))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThatThrownBy(() -> service.history(7L,12L,WaterMeterWidgetQueryService.Type.WATER_METER_TOTAL,"total_m3",168,now))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThatThrownBy(() -> service.latest(null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        verifyNoInteractions(measurements);
    }
}
