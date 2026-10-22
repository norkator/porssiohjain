/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlHeatPumpEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlMode;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.Status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Side-effect-free evaluation shared by live commands and the configuration preview. */
public final class ControlHeatPumpRuleEvaluator {
    public static final long CONTROL_LOOKBACK_SECONDS = 30L * 60L;

    private ControlHeatPumpRuleEvaluator() { }

    public static boolean eligible(DeviceEntity device) {
        return device != null && device.isEnabled() && device.getDeviceType() == DeviceType.HEAT_PUMP;
    }

    public static boolean usesPrice(ControlHeatPumpEntity rule) {
        return rule.getComparisonType() != null && rule.getPriceLimit() != null;
    }

    public static Optional<Match> evaluate(ControlHeatPumpEntity rule,
                                           Supplier<Optional<BigDecimal>> price,
                                           BooleanSupplier controlActive) {
        return evaluate(rule, Instant.now(), price, controlActive);
    }

    public static Optional<Match> evaluate(ControlHeatPumpEntity rule, Instant time,
                                           Supplier<Optional<BigDecimal>> price,
                                           BooleanSupplier controlActive) {
        if (!eligible(rule.getDevice())) return Optional.empty();
        if (rule.getActiveFrom() != null && rule.getActiveUntil() != null) {
            LocalTime local = time.atZone(ZoneId.of(rule.getControl().getTimezone())).toLocalTime();
            boolean active = rule.getActiveFrom().isBefore(rule.getActiveUntil())
                    ? !local.isBefore(rule.getActiveFrom()) && local.isBefore(rule.getActiveUntil())
                    : !local.isBefore(rule.getActiveFrom()) || local.isBefore(rule.getActiveUntil());
            if (rule.isOutsideActiveTime()) return active ? Optional.empty() : Optional.of(new Match(false, null));
            if (!active) return Optional.empty();
        } else if (rule.isOutsideActiveTime()) return Optional.empty();
        if (usesPrice(rule)) {
            return price.get().filter(value -> rule.getComparisonType() == ComparisonType.LESS_THAN
                            ? value.compareTo(rule.getPriceLimit()) < 0 : value.compareTo(rule.getPriceLimit()) > 0)
                    .map(value -> new Match(true, value));
        }
        return controlActive.getAsBoolean() ? Optional.of(new Match(false, null)) : Optional.empty();
    }

    public static boolean controlActive(ControlEntity control, Instant time, Collection<ControlTableEntity> rows) {
        if (control.getMode() == ControlMode.MANUAL) return control.isManualOn();
        if (!control.getMode().usesGeneratedControlTable()) return false;
        return rows.stream().anyMatch(row -> row.getStatus() == Status.FINAL
                && row.getStartTime().isAfter(time.minusSeconds(CONTROL_LOOKBACK_SECONDS))
                && !time.isBefore(row.getStartTime()) && time.isBefore(row.getEndTime()));
    }

    /** Keep legacy cross-control precedence, then apply the explicit order within each pump's rule set. */
    public static List<ControlHeatPumpEntity> ordered(Collection<ControlHeatPumpEntity> rules) {
        List<ControlHeatPumpEntity> sorted = rules.stream().sorted(Comparator.comparing(ControlHeatPumpEntity::getId,
                Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        Map<String, List<ControlHeatPumpEntity>> groups = new LinkedHashMap<>();
        for (var rule : sorted) {
            String key = rule.getControl().getId() + ":" + rule.getDevice().getId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rule);
        }
        Set<String> emitted = new HashSet<>();
        List<ControlHeatPumpEntity> result = new ArrayList<>();
        for (var rule : sorted) {
            String key = rule.getControl().getId() + ":" + rule.getDevice().getId();
            var group = groups.get(key);
            if (group.stream().noneMatch(row -> row.getRuleOrder() != null)) result.add(rule);
            else if (emitted.add(key)) {
                group.sort(Comparator.comparing(row -> row.getRuleOrder() == null ? Long.MAX_VALUE : row.getRuleOrder().longValue()));
                result.addAll(group);
            }
        }
        return result;
    }

    public record Match(boolean priceBased, BigDecimal price) {
        public String schedulerReason(ControlHeatPumpEntity rule) {
            if (rule.isOutsideActiveTime()) return "outside configured daily window";
            return priceBased ? String.format("price %s %s (actual=%s)",
                    rule.getComparisonType(), rule.getPriceLimit(), price) : "control schedule currently active";
        }
    }
}
