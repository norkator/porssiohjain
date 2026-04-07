/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/controls")
@RequiredArgsConstructor
@RequireAuth
public class ControlsController {

    private final AuthContext authContext;
    private final ControlService controlService;

    @GetMapping
    public List<ControlResponse> listControls() {
        return controlService.getAllControls(authContext.getAccountId());
    }

    @PostMapping
    public ControlEntity createControl(
            @RequestBody CreateControlRequest request
    ) {
        Long accountId = authContext.getAccountId();
        return controlService.createControl(
                accountId,
                request.getName(),
                request.getTimezone(),
                request.getMaxPriceSnt(),
                request.getMinPriceSnt(),
                request.getDailyOnMinutes(),
                request.getTaxPercent(),
                request.getMode(),
                request.getManualOn(),
                request.getAlwaysOnBelowMinPrice()
        );
    }

    @GetMapping("/{controlId}")
    public ControlResponse getControl(
            @PathVariable Long controlId
    ) {
        return controlService.getControl(authContext.getAccountId(), controlId);
    }

    @PutMapping("/{controlId}")
    public ControlResponse updateControl(
            @PathVariable Long controlId,
            @RequestBody UpdateControlRequest request
    ) {
        Long accountId = authContext.getAccountId();
        controlService.updateControl(
                accountId,
                controlId,
                request.getName(),
                request.getMaxPriceSnt(),
                request.getMinPriceSnt(),
                request.getDailyOnMinutes(),
                request.getTaxPercent(),
                request.getMode(),
                request.getManualOn(),
                request.getAlwaysOnBelowMinPrice(),
                null,
                null,
                null
        );
        return controlService.getControl(accountId, controlId);
    }

    @DeleteMapping("/{controlId}")
    public void deleteControl(
            @PathVariable Long controlId
    ) {
        controlService.deleteControl(authContext.getAccountId(), controlId);
    }

    @GetMapping("/{controlId}/links")
    public List<ControlDeviceResponse> getControlLinks(
            @PathVariable Long controlId
    ) {
        return controlService.getControlDeviceLinks(authContext.getAccountId(), controlId);
    }

    @PostMapping("/{controlId}/links/devices")
    public ControlDeviceResponse addDeviceLink(
            @PathVariable Long controlId,
            @RequestBody ControlDeviceLinkRequest request
    ) {
        return controlService.addDeviceToControl(
                authContext.getAccountId(),
                controlId,
                request.getDeviceId(),
                request.getDeviceChannel(),
                request.getEstimatedPowerKw()
        );
    }
}
