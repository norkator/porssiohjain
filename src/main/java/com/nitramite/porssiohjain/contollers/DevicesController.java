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
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.models.CreateDeviceRequest;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@RequireAuth
public class DevicesController {

    private final AuthContext authContext;
    private final DeviceService deviceService;

    @GetMapping
    public List<DeviceResponse> listDevices() {
        Long accountId = authContext.getAccountId();
        return deviceService.listDevices(accountId, accountId);
    }

    @PostMapping
    public DeviceResponse createDevice(
            @RequestBody CreateDeviceRequest request
    ) {
        Long accountId = authContext.getAccountId();
        return deviceService.createDevice(
                accountId,
                accountId,
                request.getDeviceName(),
                request.getTimezone(),
                request.getDeviceType(),
                request.getEnabled() != null ? request.getEnabled() : true,
                request.getHpName(),
                request.getAcType(),
                request.getAcUsername(),
                request.getAcPassword(),
                request.getAcDeviceId(),
                request.getBuildingId()
        );
    }

    @GetMapping("/{deviceId}")
    public DeviceResponse getDevice(
            @PathVariable Long deviceId
    ) {
        return deviceService.getDevice(authContext.getAccountId(), deviceId);
    }

    @PutMapping("/{deviceId}")
    public DeviceResponse updateDevice(
            @PathVariable Long deviceId,
            @RequestBody CreateDeviceRequest request
    ) {
        Long accountId = authContext.getAccountId();
        deviceService.updateDevice(
                accountId,
                deviceId,
                request.getDeviceName(),
                request.getTimezone(),
                request.getDeviceType(),
                request.getEnabled() != null ? request.getEnabled() : true,
                request.getHpName(),
                request.getAcType(),
                request.getAcUsername(),
                request.getAcPassword(),
                request.getAcDeviceId(),
                request.getBuildingId()
        );
        return deviceService.getDevice(accountId, deviceId);
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> deleteDevice(
            @PathVariable Long deviceId
    ) {
        deviceService.deleteDevice(authContext.getAccountId(), deviceId);
        return ResponseEntity.noContent().build();
    }
}
