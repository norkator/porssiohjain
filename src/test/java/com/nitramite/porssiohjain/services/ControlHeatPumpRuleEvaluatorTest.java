package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ControlHeatPumpRuleEvaluatorTest {
    @Test
    void finalScheduleIncludesExactLocalMidnightAndExcludesEnd() {
        Instant midnight = Instant.parse("2026-10-24T21:00:00Z"); // Helsinki midnight before autumn DST change.
        var control = ControlEntity.builder().mode(ControlMode.CHEAPEST_HOURS).timezone("Europe/Helsinki").build();
        var row = ControlTableEntity.builder().status(Status.FINAL).startTime(midnight)
                .endTime(midnight.plusSeconds(900)).build();

        assertThat(ControlHeatPumpRuleEvaluator.controlActive(control, midnight, List.of(row))).isTrue();
        assertThat(ControlHeatPumpRuleEvaluator.controlActive(control, midnight.minusNanos(1), List.of(row))).isFalse();
        assertThat(ControlHeatPumpRuleEvaluator.controlActive(control, row.getEndTime(), List.of(row))).isFalse();
        row.setStatus(Status.PLANNED);
        assertThat(ControlHeatPumpRuleEvaluator.controlActive(control, midnight, List.of(row))).isFalse();
    }

    @Test
    void priceRuleUsesStrictComparisonAndDoesNotDependOnControlActivity() {
        var device = new DeviceEntity();
        device.setEnabled(true);
        device.setDeviceType(DeviceType.HEAT_PUMP);
        var rule = ControlHeatPumpEntity.builder().device(device).comparisonType(ComparisonType.LESS_THAN)
                .priceLimit(BigDecimal.TEN).build();

        assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, () -> Optional.of(BigDecimal.ONE),
                () -> { throw new AssertionError("A price rule must not consult schedule activity"); })).isPresent();
        assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, () -> Optional.of(BigDecimal.TEN), () -> true)).isEmpty();
        assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, Optional::empty, () -> true)).isEmpty();
        rule.setComparisonType(ComparisonType.GREATER_THAN);
        assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, () -> Optional.of(BigDecimal.TEN), () -> true)).isEmpty();
    }

    @Test
    void disabledDeviceDoesNotReadPricesAndManualControlDoesNotNeedScheduleRows() {
        var device = new DeviceEntity();
        device.setEnabled(false);
        device.setDeviceType(DeviceType.HEAT_PUMP);
        var rule = ControlHeatPumpEntity.builder().device(device).build();
        assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule,
                () -> { throw new AssertionError("Disabled device"); }, () -> true)).isEmpty();
        var control = ControlEntity.builder().mode(ControlMode.MANUAL).manualOn(true).build();
        assertThat(ControlHeatPumpRuleEvaluator.controlActive(control, Instant.now(), List.of())).isTrue();
        control.setManualOn(false);
        assertThat(ControlHeatPumpRuleEvaluator.controlActive(control, Instant.now(), List.of())).isFalse();
        control.setMode(ControlMode.SCHEDULED);
        assertThat(ControlHeatPumpRuleEvaluator.controlActive(control, Instant.now(), List.of())).isFalse();
    }
    @Test
    void dailyWindowIsHalfOpenAndOutsideActionDoesNotNeedPriceOrActiveControl() {
        var control = ControlEntity.builder().id(1L).timezone("Europe/Helsinki").build();
        var device = new DeviceEntity();
        device.setEnabled(true);
        device.setDeviceType(DeviceType.HEAT_PUMP);
        var rule = ControlHeatPumpEntity.builder().control(control).device(device)
                .activeFrom(LocalTime.of(7, 5)).activeUntil(LocalTime.of(22, 10))
                .comparisonType(ComparisonType.LESS_THAN).priceLimit(BigDecimal.TEN).build();
        var start = Instant.parse("2026-10-22T04:05:00Z");
        var end = Instant.parse("2026-10-22T19:10:00Z");
        assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, start, () -> Optional.of(BigDecimal.ONE), () -> false)).isPresent();
        assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, start.minusNanos(1), () -> Optional.of(BigDecimal.ONE), () -> true)).isEmpty();
        assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, end, () -> Optional.of(BigDecimal.ONE), () -> true)).isEmpty();
        rule.setOutsideActiveTime(true);
        assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, end, () -> { throw new AssertionError("outside window needs no price"); },
                () -> { throw new AssertionError("outside window needs no final schedule"); })).isPresent();
        assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, start, Optional::empty, () -> true)).isEmpty();
    }

    @Test
    void overnightWindowUsesLocalClockAcrossBothOccurrencesOfDstHour() {
        var control = ControlEntity.builder().timezone("Europe/Helsinki").build();
        var device = new DeviceEntity();
        device.setEnabled(true);
        device.setDeviceType(DeviceType.HEAT_PUMP);
        var rule = ControlHeatPumpEntity.builder().control(control).device(device).activeFrom(LocalTime.of(22, 0))
                .activeUntil(LocalTime.of(3, 30)).comparisonType(ComparisonType.LESS_THAN).priceLimit(BigDecimal.TEN).build();
        for (String time : List.of("2026-10-24T19:00:00Z", "2026-10-25T00:15:00Z", "2026-10-25T01:15:00Z")) {
            assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, Instant.parse(time), () -> Optional.of(BigDecimal.ONE), () -> false)).isPresent();
        }
        for (String time : List.of("2026-10-25T00:45:00Z", "2026-10-25T01:30:00Z", "2026-10-25T18:59:59Z")) {
            assertThat(ControlHeatPumpRuleEvaluator.evaluate(rule, Instant.parse(time), () -> Optional.of(BigDecimal.ONE), () -> true)).isEmpty();
        }
    }

    @Test
    void explicitRuleOrderOverridesIdsButLegacyInterleavedRulesKeepTheirPriority() {
        var device = new DeviceEntity();
        device.setId(1L);
        var first = ControlEntity.builder().id(1L).build();
        var second = ControlEntity.builder().id(2L).build();
        var a = ControlHeatPumpEntity.builder().id(10L).control(first).device(device).build();
        var b = ControlHeatPumpEntity.builder().id(20L).control(second).device(device).build();
        var c = ControlHeatPumpEntity.builder().id(30L).control(first).device(device).build();
        assertThat(ControlHeatPumpRuleEvaluator.ordered(List.of(c, b, a))).containsExactly(a, b, c);
        a.setRuleOrder(1);
        c.setRuleOrder(0);
        assertThat(ControlHeatPumpRuleEvaluator.ordered(List.of(c, b, a))).containsExactly(c, a, b);
    }

}
