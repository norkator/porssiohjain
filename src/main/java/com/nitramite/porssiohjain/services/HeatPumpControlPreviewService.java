/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.Status;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse;
import com.nitramite.porssiohjain.services.models.ControlHeatPumpRulesRequest;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse.*;
import com.nitramite.porssiohjain.services.nordpool.NordpoolMarket;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeatPumpControlPreviewService {
    private final ControlService controlService;
    private final ControlRepository controlRepository;
    private final ControlHeatPumpRepository rules;
    private final ControlTableRepository controlTableRepository;
    private final NordpoolRepository nordpoolRepository;
    private final DeviceAcDataRepository acDataRepository;
    private final WeatherControlHeatPumpRepository weatherRules;
    private final ProductionSourceHeatPumpRepository productionRules;
    private final ControlPriceService controlPriceService;
    private final HeatPumpPreviewStateDecoder stateDecoder;

    public HeatPumpControlPreviewResponse preview(Long accountId, Long controlId) {
        return preview(accountId, controlId, Instant.now());
    }

    HeatPumpControlPreviewResponse preview(Long accountId, Long controlId, Instant now) {
        return preview(accountId, controlId, now, null, null);
    }

    public HeatPumpControlPreviewResponse previewDraft(Long accountId, Long controlId, Long deviceId,
                                                       ControlHeatPumpRulesRequest request) {
        var draft = controlService.prepareHeatPumpRules(accountId, controlId, deviceId, request);
        // Stable synthetic IDs are used only by the graph; preview never writes or sends commands.
        long id = -1;
        for (var row : draft) if (row.getId() == null) row.setId(id--);
        return preview(accountId, controlId, Instant.now(), deviceId, draft);
    }

    private HeatPumpControlPreviewResponse preview(Long accountId, Long controlId, Instant now,
                                                   Long draftDevice, List<ControlHeatPumpEntity> draft) {
        // Use the same owned/shared-control authorization as the view before reading any rule or device.
        controlService.getControl(accountId, controlId);
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found"));
        ZoneId zone = ZoneId.of(control.getTimezone());
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(2).atStartOfDay(zone).toInstant();
        List<NordpoolEntity> market = nordpoolRepository
                .findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(
                        NordpoolMarket.normalize(control.getAccount().getMarketIndexName()), start, end)
                .stream().sorted(Comparator.comparing(NordpoolEntity::getDeliveryStart)).toList();
        List<ControlTableEntity> schedule = controlTableRepository.findActivePeriodsOverlapping(
                controlId, Status.FINAL, start, end);

        List<ControlHeatPumpEntity> configured = new ArrayList<>(rules.findByControlIdOrderByIdAsc(controlId));
        if (draftDevice != null) {
            configured.removeIf(rule -> rule.getDevice().getId().equals(draftDevice));
            configured.addAll(draft);
        }
        configured = ControlHeatPumpRuleEvaluator.ordered(configured);

        // Include authoritative delivery and schedule boundaries as well as quarter-hour points.
        // Walking instants between local midnights also covers 23/25-hour days without duplicate timestamps.
        SortedSet<Instant> boundaries = new TreeSet<>(List.of(start, now, end));
        for (Instant time = start; time.isBefore(end); time = time.plusSeconds(900)) boundaries.add(time);
        market.forEach(price -> {
            addBoundary(boundaries, price.getDeliveryStart(), start, end);
            addBoundary(boundaries, price.getDeliveryEnd(), start, end);
        });
        schedule.forEach(row -> {
            addBoundary(boundaries, row.getStartTime(), start, end);
            addBoundary(boundaries, row.getEndTime(), start, end);
            addBoundary(boundaries, row.getStartTime().plusSeconds(
                    ControlHeatPumpRuleEvaluator.CONTROL_LOOKBACK_SECONDS), start, end);
        });
        for (var rule : configured) {
            for (LocalDate date = today.minusDays(1); !date.isAfter(today.plusDays(2)); date = date.plusDays(1)) {
                addLocalBoundary(boundaries, date, rule.getActiveFrom(), zone, start, end);
                addLocalBoundary(boundaries, date, rule.getActiveUntil(), zone, start, end);
            }
        }
        List<Instant> times = List.copyOf(boundaries);
        List<PricePeriod> prices = new ArrayList<>();
        for (int i = 0; i < times.size() - 1; i++) {
            Instant time = times.get(i);
            BigDecimal combined = market.stream()
                    .filter(price -> !time.isBefore(price.getDeliveryStart()) && time.isBefore(price.getDeliveryEnd()))
                    .findFirst().map(price -> controlPriceService.getCombinedPrice(control, price)).orElse(null);
            prices.add(new PricePeriod(time, times.get(i + 1), combined));
        }

        var byDevice = configured.stream()
                .collect(Collectors.groupingBy(rule -> rule.getDevice().getId(), LinkedHashMap::new, Collectors.toList()));
        List<Pump> pumps = new ArrayList<>();
        for (List<ControlHeatPumpEntity> deviceRules : byDevice.values()) {
            DeviceEntity device = deviceRules.getFirst().getDevice();
            DeviceAcDataEntity acData = acDataRepository.findByDevice(device).orElse(null);
            Map<Long, State> states = new HashMap<>();
            if (acData != null) deviceRules.forEach(rule -> states.put(rule.getId(),
                    stateDecoder.decode(acData.getAcType(), rule.getStateHex())));
            List<Period> periods = project(control, device, deviceRules, schedule, prices, states, acData != null, now);
            boolean competing = rules.findByDevice(device).stream()
                    .anyMatch(rule -> !Objects.equals(rule.getControl().getId(), controlId))
                    || !weatherRules.findByDevice(device).isEmpty() || !productionRules.findByDevice(device).isEmpty();
            pumps.add(new Pump(device.getId(), device.getDeviceName(), competing, periods));
        }
        return new HeatPumpControlPreviewResponse(zone.getId(), now, start, end, List.copyOf(prices), List.copyOf(pumps));
    }

    private List<Period> project(ControlEntity control, DeviceEntity device, List<ControlHeatPumpEntity> deviceRules,
                                 List<ControlTableEntity> schedule, List<PricePeriod> prices, Map<Long, State> states,
                                 boolean hasAcData, Instant now) {
        List<Period> result = new ArrayList<>();
        State lastState = null;
        String lastRaw = null;
        for (PricePeriod price : prices) {
            if (price.start().isBefore(now)) continue;
            ControlHeatPumpEntity winner = null;
            Reason reason;
            if (!ControlHeatPumpRuleEvaluator.eligible(device)) reason = Reason.DISABLED;
            else if (!hasAcData) reason = Reason.NO_AC_DATA;
            else {
                // Live scheduling processes control rules in ascending ID order; the first match wins.
                winner = deviceRules.stream().filter(rule -> ControlHeatPumpRuleEvaluator.evaluate(rule, price.start(),
                        () -> Optional.ofNullable(price.price()),
                        () -> ControlHeatPumpRuleEvaluator.controlActive(control, price.start(), schedule)).isPresent())
                        .findFirst().orElse(null);
                reason = winner != null ? (winner.isOutsideActiveTime() ? Reason.INACTIVE_TIME
                        : ControlHeatPumpRuleEvaluator.usesPrice(winner) ? Reason.PRICE
                        : Reason.SCHEDULE)
                        : price.price() == null && deviceRules.stream().anyMatch(ControlHeatPumpRuleEvaluator::usesPrice)
                        ? Reason.MISSING_PRICE : Reason.NO_MATCH;
            }
            if (winner != null) {
                lastState = states.get(winner.getId());
                lastRaw = winner.getStateHex();
            }
            Period next = new Period(price.start(), price.end(), winner == null ? null : winner.getId(), reason,
                    winner == null ? null : winner.getComparisonType(), winner == null ? null : winner.getPriceLimit(),
                    lastRaw, lastState, winner == null);
            if (!result.isEmpty() && sameSetting(result.getLast(), next)) {
                Period previous = result.removeLast();
                next = new Period(previous.start(), next.end(), next.ruleId(), next.reason(), next.comparison(),
                        next.threshold(), next.stateHex(), next.state(), next.assumed());
            }
            result.add(next);
        }
        return List.copyOf(result);
    }

    private boolean sameSetting(Period left, Period right) {
        return left.end().equals(right.start()) && Objects.equals(left.ruleId(), right.ruleId())
                && left.reason() == right.reason() && Objects.equals(left.stateHex(), right.stateHex());
    }

    private void addLocalBoundary(Set<Instant> boundaries, LocalDate date, LocalTime time, ZoneId zone,
                                  Instant start, Instant end) {
        if (time == null) return;
        var local = date.atTime(time);
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) addBoundary(boundaries, local.atZone(zone).toInstant(), start, end);
        else offsets.forEach(offset -> addBoundary(boundaries, local.toInstant(offset), start, end));
        // Clock changes can themselves cross a daily window boundary during repeated/skipped local times.
        var transition = zone.getRules().nextTransition(date.atStartOfDay(zone).toInstant().minusNanos(1));
        if (transition != null) addBoundary(boundaries, transition.getInstant(), start, end);
    }

    private void addBoundary(Set<Instant> boundaries, Instant time, Instant start, Instant end) {
        if (!time.isBefore(start) && !time.isAfter(end)) boundaries.add(time);
    }
}
