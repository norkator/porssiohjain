package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.services.models.HeatingComfortSchedule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class HeatingComfortScheduleTest {
    private final ZoneId zone = ZoneId.of("Europe/Helsinki");

    @Test
    void morningWindowIncludesStartExcludesEndAndRepeatsTomorrow() {
        var schedule = schedule("21", period("06:00", "09:00", "22"));
        for (var date : List.of(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 16))) {
            assertTarget(schedule, date.atTime(5, 59).atZone(zone).toInstant(), "21");
            assertTarget(schedule, date.atTime(6, 0).atZone(zone).toInstant(), "22");
            assertTarget(schedule, date.atTime(8, 59).atZone(zone).toInstant(), "22");
            assertTarget(schedule, date.atTime(9, 0).atZone(zone).toInstant(), "21");
        }
        assertTarget(schedule("21"), Instant.EPOCH, "21");
    }

    @Test
    void overnightAndAdjacentWindowsUseTheDefaultInGaps() {
        var schedule = schedule("21", period("22:00", "06:00", "19"), period("06:00", "09:00", "22"));
        assertTarget(schedule, Instant.parse("2026-01-15T22:00:00Z"), "19");
        assertTarget(schedule, Instant.parse("2026-01-16T03:59:00Z"), "19");
        assertTarget(schedule, Instant.parse("2026-01-16T04:00:00Z"), "22");
        assertTarget(schedule, Instant.parse("2026-01-16T07:00:00Z"), "21");
    }

    @Test
    void followsLocalClockAcrossBothDstTransitions() {
        var schedule = schedule("21", period("03:00", "05:00", "22"));
        assertTarget(schedule, Instant.parse("2026-03-29T00:59:00Z"), "21");
        assertTarget(schedule, Instant.parse("2026-03-29T01:00:00Z"), "22");
        assertTarget(schedule, Instant.parse("2026-03-29T02:00:00Z"), "21");
        // Both occurrences of 03:30 on the 25-hour day use the same local target.
        assertTarget(schedule, Instant.parse("2026-10-25T00:30:00Z"), "22");
        assertTarget(schedule, Instant.parse("2026-10-25T01:30:00Z"), "22");
        assertTarget(schedule, Instant.parse("2026-10-25T03:00:00Z"), "21");
    }

    @Test
    void rejectsOverlapsIncludingAcrossMidnightAndAmbiguousFullDayWindows() {
        assertThatThrownBy(() -> schedule("21", period("22:00", "06:00", "19"), period("05:00", "09:00", "22")))
                .hasMessage("heatingPlanner.comfort.overlap");
        assertThatThrownBy(() -> schedule("21", period("06:00", "06:00", "22")))
                .hasMessage("heatingPlanner.comfort.invalidTimes");
        assertThatThrownBy(() -> schedule("36")).hasMessage("heatingPlanner.comfort.invalidTemperature");
    }

    @Test
    void simulationChangesFloorTargetsAndComfortMaximumOnlyWithinScheduledWindow() {
        var schedule = schedule("21", period("06:00", "09:00", "22"));
        var service = new HeatingPlanSimulationService();
        var settings = new HeatingPlanSimulationService.Settings(Duration.ofMinutes(15), new BigDecimal("5"),
                new BigDecimal("20"), new BigDecimal("23"), new BigDecimal("27"), new BigDecimal("29"),
                new BigDecimal("19"), new BigDecimal("20"), new BigDecimal("23.5"), new BigDecimal("5"), List.of());
        var model = new HeatingPlanSimulationService.ThermalModel(BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var market = List.of("2026-01-15T03:45:00Z", "2026-01-15T04:00:00Z", "2026-01-15T07:00:00Z").stream()
                .map(time -> new HeatingPlanSimulationService.MarketPoint(Instant.parse(time), BigDecimal.TEN,
                        BigDecimal.ZERO, BigDecimal.ZERO)).toList();
        var request = new HeatingPlanSimulationService.SimulationRequest(new BigDecimal("22"), new BigDecimal("21"),
                settings, model, market, null, true, true);

        var constant = service.simulate(request);
        assertThat(constant.points()).allSatisfy(point -> {
            assertThat(point.floorSetpoint()).isEqualByComparingTo("23");
            assertThat(point.comfortTarget()).isNull();
        });
        var result = service.simulate(request.withComfortSchedule(schedule, zone));
        assertThat(result.points()).extracting(point -> point.floorSetpoint().intValue()).containsExactly(23, 24, 23);
        assertThat(result.points()).extracting(point -> point.comfortTarget().intValue()).containsExactly(21, 22, 21);
        assertThat(result.points().get(1).reason()).contains("scheduled comfort target 22");
        // A higher scheduled target changes the maximum, without changing the configured minimum.
        assertThat(settings.withComfortSchedule(schedule, zone).atTime(market.get(1).time()).maximumRoomTemperature())
                .isEqualByComparingTo("24.5");
        assertThat(settings.withComfortSchedule(schedule, zone).atTime(market.get(1).time()).minimumRoomTemperature())
                .isEqualByComparingTo("20");
        assertThat(settings.withComfortSchedule(schedule("35"), zone).atTime(market.get(1).time()).normalFloorTemperature())
                .isEqualByComparingTo("27");
    }

    private void assertTarget(HeatingComfortSchedule schedule, Instant instant, String expected) {
        assertThat(schedule.targetAt(instant, zone)).isEqualByComparingTo(expected);
    }

    private HeatingComfortSchedule schedule(String fallback, HeatingComfortSchedule.Period... periods) {
        return new HeatingComfortSchedule(new BigDecimal(fallback), List.of(periods));
    }

    private HeatingComfortSchedule.Period period(String from, String to, String target) {
        return new HeatingComfortSchedule.Period(LocalTime.parse(from), LocalTime.parse(to), new BigDecimal(target));
    }
}
