package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
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
}
