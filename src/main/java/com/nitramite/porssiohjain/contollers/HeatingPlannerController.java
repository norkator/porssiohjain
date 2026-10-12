/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerWidgetQueryService;
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

    @GetMapping("/widget-summary")
    public HeatingPlannerWidgetSummaryResponse widgetSummary(
            @RequestParam Long siteId, @RequestParam(required = false) Long roomId) {
        return queryService.getSummary(authContext.getAccountId(), siteId, roomId, Instant.now());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidWidgetParameters(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "error", "Bad Request",
                "message", "siteId is required; siteId and roomId must be positive integers"));
    }
}
