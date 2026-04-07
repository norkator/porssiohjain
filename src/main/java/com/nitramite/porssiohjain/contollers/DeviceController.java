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
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.HeatPumpAcDeviceSelectionService;
import com.nitramite.porssiohjain.services.models.CreateDeviceRequest;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.HeatPumpAcDeviceResponse;
import com.nitramite.porssiohjain.services.models.HeatPumpAcDevicesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final HeatPumpAcDeviceSelectionService heatPumpAcDeviceSelectionService;
    private final AuthContext authContext;

    @RequireAuth
    @PostMapping("/create/{accountId}")
    public ResponseEntity<DeviceResponse> createDevice(
            @PathVariable Long accountId,
            @RequestBody CreateDeviceRequest request
    ) {
        Long authAccountId = authContext.getAccountId();
        DeviceResponse device = deviceService.createDevice(
                authAccountId, accountId, request.getDeviceName(), request.getTimezone(), request.getDeviceType(),
                request.getEnabled() != null ? request.getEnabled() : true,
                request.getHpName(), request.getAcType(), request.getAcUsername(), request.getAcPassword(),
                request.getAcDeviceId(), request.getBuildingId()

        );
        return ResponseEntity.ok(device);
    }

    @RequireAuth
    @GetMapping("/list/{accountId}")
    public ResponseEntity<List<DeviceResponse>> listDevices(
            @PathVariable Long accountId
    ) {
        Long authAccountId = authContext.getAccountId();
        return ResponseEntity.ok(deviceService.listDevices(authAccountId, accountId));
    }

    @RequireAuth
    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> getDevice(
            @PathVariable Long deviceId
    ) {
        Long accountId = authContext.getAccountId();
        return ResponseEntity.ok(deviceService.getDevice(accountId, deviceId));
    }

    @RequireAuth
    @PostMapping("/heat-pump/ac-devices")
    public ResponseEntity<List<HeatPumpAcDeviceResponse>> listSelectableHeatPumpAcDevices(
            @RequestBody HeatPumpAcDevicesRequest request
    ) {
        authContext.getAccountId();
        return ResponseEntity.ok(heatPumpAcDeviceSelectionService.getSelectableDevices(request));
    }

}
