/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.HeatingPlannerPlanPointEntity;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.HeatingPlannerWidgetSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HeatingPlannerMonitorService {
    private final HeatingPlannerConfigurationService configuration;
    private final HeatingPlannerWidgetQueryService widgets;
    private final HeatingPlannerRoomRepository rooms;
    private final HeatingPlannerPlanPointRepository points;

    public Long defaultSite(Long accountId) {
        return configuration.preferredSiteId(accountId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Configure Heating Planner in the desktop first"));
    }

    @Transactional(readOnly = true)
    public Monitor monitor(Long accountId, Instant now) {
        Long siteId = defaultSite(accountId);
        var summary = widgets.getSummary(accountId, siteId, null, now);
        ZoneId zone = ZoneId.of(summary.timezone());
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant midnight = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(2).atStartOfDay(zone).toInstant();
        var history = points.findLatestHistoricalPredictions(accountId, siteId, start, now);
        List<Room> result = new ArrayList<>();
        for (var room : rooms.findByAccountIdAndSiteIdOrderBySortOrderAscIdAsc(accountId, siteId)) {
            var current = summary.rooms().stream().filter(r -> r.roomId().equals(room.getId())).findFirst().orElse(null);
            var version = current != null && current.currentPoint() != null ? current.currentPoint().planVersion()
                    : summary.latestPlan() == null ? null : summary.latestPlan().planVersion();
            TreeMap<Instant, Point> series = new TreeMap<>();
            history.stream().filter(p -> p.getRoom().getId().equals(room.getId()))
                    .forEach(p -> series.put(p.getPlannedTime(), point(p)));
            if (version != null) points.findByPlanVersionAndRoomIdAndPlannedTimeBetweenOrderByPlannedTimeAsc(
                    version, room.getId(), start, end).stream()
                    .filter(p -> p.getPlannedTime().isBefore(end) && p.getPlannedTime().plusSeconds(900).isAfter(now))
                    .forEach(p -> series.put(p.getPlannedTime(), point(p)));
            result.add(new Room(room.getId(), room.getName(), room.getTargetRoomTemperature(),
                    room.getComfortSchedule() == null ? null : room.getComfortSchedule().getId(), List.copyOf(series.values())));
        }
        return new Monitor(summary, start, midnight, end, result, configuration.comfortSchedules(accountId, siteId));
    }

    private Point point(HeatingPlannerPlanPointEntity p) {
        return new Point(p.getPlannedTime(), p.getPriceCentsPerKwh(), p.getOutdoorTemperature(),
                p.getPredictedRoomTemperature(), p.getPredictedFloorTemperature(), p.getPlannedFloorSetpoint(),
                p.getPlannedHeatPumpSetpoint(), p.getComfortTarget(), p.getPredictedWoodHeatRate(),
                p.getOperatingMode().name(), p.getReason());
    }
    public record Monitor(HeatingPlannerWidgetSummaryResponse summary, Instant start, Instant tomorrow, Instant end,
                          List<Room> rooms, List<HeatingPlannerConfigurationService.ComfortScheduleConfiguration> schedules) { }
    public record Room(Long id, String name, BigDecimal target, Long scheduleId, List<Point> points) { }
    public record Point(Instant time, BigDecimal price, BigDecimal outdoor, BigDecimal room, BigDecimal floor,
                        BigDecimal floorSetpoint, BigDecimal heatPumpSetpoint, BigDecimal target,
                        BigDecimal wood, String mode, String reason) { }
}
