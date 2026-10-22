/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.HeatPumpControlPreviewService;
import com.nitramite.porssiohjain.services.models.ControlHeatPumpRulesRequest;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/controls")
@RequiredArgsConstructor
@RequireAuth
public class HeatPumpRulesController {
    private final AuthContext authContext;
    private final HeatPumpControlPreviewService previews;

    @GetMapping("/{controlId}/heat-pumps/preview")
    public HeatPumpControlPreviewResponse preview(@PathVariable Long controlId) {
        return previews.preview(authContext.getAccountId(), controlId);
    }

    @PostMapping("/{controlId}/heat-pumps/{deviceId}/preview")
    public HeatPumpControlPreviewResponse previewDraft(@PathVariable Long controlId, @PathVariable Long deviceId,
                                                       @RequestBody ControlHeatPumpRulesRequest request) {
        return previews.previewDraft(authContext.getAccountId(), controlId, deviceId, request);
    }
}
