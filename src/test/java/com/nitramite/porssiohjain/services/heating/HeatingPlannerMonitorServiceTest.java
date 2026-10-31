package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.repository.HeatingPlannerRoomRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanPointRepository;
import com.nitramite.porssiohjain.services.models.HeatingPlannerWidgetSummaryResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class HeatingPlannerMonitorServiceTest {
    @ParameterizedTest
    @CsvSource({"2026-03-29,47,23", "2026-10-25,49,25"})
    void usesControlLocalCalendarBoundariesAcrossDst(String date, long horizonHours, long todayHours) {
        var configuration = mock(HeatingPlannerConfigurationService.class);
        var widgets = mock(HeatingPlannerWidgetQueryService.class);
        var rooms = mock(HeatingPlannerRoomRepository.class);
        var points = mock(HeatingPlannerPlanPointRepository.class);
        var summary = mock(HeatingPlannerWidgetSummaryResponse.class);
        ZoneId zone = ZoneId.of("Europe/Helsinki");
        Instant now = LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant();
        when(configuration.preferredSiteId(1L)).thenReturn(Optional.of(7L));
        when(widgets.getSummary(1L, 7L, null, now)).thenReturn(summary);
        when(summary.timezone()).thenReturn(zone.getId());
        var service = new HeatingPlannerMonitorService(configuration, widgets, rooms, points);
        var result = service.monitor(1L, now);
        assertThat(result.start()).isEqualTo(LocalDate.parse(date).atStartOfDay(zone).toInstant());
        assertThat(Duration.between(result.start(), result.tomorrow()).toHours()).isEqualTo(todayHours);
        assertThat(Duration.between(result.start(), result.end()).toHours()).isEqualTo(horizonHours);
        verify(points).findLatestHistoricalPredictions(1L, 7L, result.start(), now);
        verify(rooms).findByAccountIdAndSiteIdOrderBySortOrderAscIdAsc(1L, 7L);
        verify(configuration).comfortSchedules(1L, 7L);
        verifyNoMoreInteractions(points, rooms);
    }
}
