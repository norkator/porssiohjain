/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerWidgetQueryService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerMonitorService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerConfigurationService;
import java.math.BigDecimal;
import com.nitramite.porssiohjain.services.models.HeatingPlannerWidgetSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/heating-planner")
@RequiredArgsConstructor
@RequireAuth
public class HeatingPlannerController {
    private final AuthContext authContext;
    private final HeatingPlannerWidgetQueryService queryService;

    private final HeatingPlannerMonitorService monitorService;
    private final HeatingPlannerConfigurationService configurationService;

    @GetMapping("/monitor")
    public HeatingPlannerMonitorService.Monitor monitor() {
        return monitorService.monitor(authContext.getAccountId(), Instant.now());
    }

    public record ComfortUpdate(BigDecimal target, Long scheduleId) { }

    @PutMapping("/rooms/{roomId}/comfort")
    public void comfort(@PathVariable Long roomId, @RequestBody ComfortUpdate update) {
        Long accountId = authContext.getAccountId();
        configurationService.updateRoomComfort(accountId, monitorService.defaultSite(accountId), roomId,
                update.target(), update.scheduleId());
    }

    @PutMapping("/comfort-schedules")
    public HeatingPlannerConfigurationService.ComfortScheduleConfiguration schedule(
            @RequestBody HeatingPlannerConfigurationService.ComfortScheduleConfiguration update) {
        Long accountId = authContext.getAccountId();
        if (update.schedule() == null) throw new IllegalArgumentException("Comfort schedule is required");
        return configurationService.saveComfortSchedule(accountId, monitorService.defaultSite(accountId), update);
    }

    @GetMapping("/widget-summary")
    public HeatingPlannerWidgetSummaryResponse widgetSummary(
            @RequestParam Long siteId, @RequestParam(required = false) Long roomId) {
        return queryService.getSummary(authContext.getAccountId(), siteId, roomId, Instant.now());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> invalidComfortBody(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "error", "Bad Request",
                "message", "Invalid comfort settings. Use targets between 5 and 35 °C and non-overlapping time windows."));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidWidgetParameters(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "error", "Bad Request",
                "message", "siteId is required; siteId and roomId must be positive integers"));
    }
}
