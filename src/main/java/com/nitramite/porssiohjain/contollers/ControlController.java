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
import com.nitramite.porssiohjain.entity.ControlDeviceEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/control")
@RequiredArgsConstructor
public class ControlController {

    private final ControlService controlService;
    private final AuthContext authContext;

    @GetMapping("/{deviceUuid}")
    public Map<Integer, Integer> controlsForDevice(
            @PathVariable String deviceUuid
    ) {
        return controlService.getControlsForDevice(deviceUuid);
    }

    @GetMapping("/{deviceUuid}/timetable")
    public TimeTableListResponse timeTableForDevice(
            @PathVariable String deviceUuid
    ) {
        return controlService.getTimetableForDevice(deviceUuid);
    }

    @RequireAuth
    @PostMapping("/create")
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

    @RequireAuth
    @PutMapping("/update/{id}")
    public ControlEntity updateControl(
            @PathVariable Long id,
            @RequestBody UpdateControlRequest request
    ) {
        Long accountId = authContext.getAccountId();
        return controlService.updateControl(
                accountId,
                id,
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
    }

    @RequireAuth
    @DeleteMapping("/delete/{id}")
    public void deleteControl(
            @PathVariable Long id
    ) {
        Long accountId = authContext.getAccountId();
        controlService.deleteControl(accountId, id);
    }

    @RequireAuth
    @GetMapping("/controls")
    public List<ControlResponse> getAllControls() {
        Long accountId = authContext.getAccountId();
        return controlService.getAllControls(accountId);
    }

    @RequireAuth
    @PostMapping("/{controlId}/create/device")
    public ControlDeviceResponse addDeviceToControl(
            @PathVariable Long controlId,
            @RequestParam Long deviceId,
            @RequestParam Integer deviceChannel
    ) {
        Long accountId = authContext.getAccountId();
        return controlService.addDeviceToControl(accountId, controlId, deviceId, deviceChannel);
    }

    @RequireAuth
    @PutMapping("/update/device/{id}")
    public ControlDeviceResponse updateControlDevice(
            @PathVariable Long id,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Integer deviceChannel
    ) {
        Long accountId = authContext.getAccountId();
        return controlService.updateControlDevice(accountId, id, deviceId, deviceChannel);
    }

    @RequireAuth
    @DeleteMapping("/delete/device/{id}")
    public void deleteControlDevice(
            @PathVariable Long id
    ) {
        Long accountId = authContext.getAccountId();
        controlService.deleteControlDevice(accountId, id);
    }

    @RequireAuth
    @GetMapping("/{controlId}/devices")
    public List<ControlDeviceEntity> getDevicesByControl(
            @PathVariable Long controlId
    ) {
        Long accountId = authContext.getAccountId();
        return controlService.getDevicesByControl(accountId, controlId);
    }

}
