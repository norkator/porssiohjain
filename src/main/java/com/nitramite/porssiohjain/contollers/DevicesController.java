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
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.HeatPumpAcDeviceSelectionService;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcStateResponse;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcStateService;
import com.nitramite.porssiohjain.services.models.CreateDeviceRequest;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.HeatPumpAcDeviceResponse;
import com.nitramite.porssiohjain.services.models.HeatPumpAcDevicesRequest;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateResponse;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateService;
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
    private final HeatPumpAcDeviceSelectionService heatPumpAcDeviceSelectionService;
    private final ToshibaAcStateService toshibaAcStateService;
    private final MitsubishiAcStateService mitsubishiAcStateService;

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

    @GetMapping("/{deviceId}/heat-pump/state")
    public HeatPumpStateResponse getHeatPumpState(
            @PathVariable Long deviceId
    ) {
        DeviceAcDataEntity acData = deviceService.getDeviceAcData(authContext.getAccountId(), deviceId);
        String currentState = null;

        if (acData.getAcType() == AcType.TOSHIBA) {
            ToshibaAcStateResponse response = toshibaAcStateService.getAcState(acData);
            if (response != null && response.getResObj() != null) {
                currentState = response.getResObj().getAcStateData();
            }
        } else if (acData.getAcType() == AcType.MITSUBISHI) {
            MitsubishiAcStateResponse response = mitsubishiAcStateService.getAcState(acData);
            if (response != null) {
                currentState = acData.getLastPolledStateHex();
            }
        } else {
            throw new IllegalArgumentException("Device is not a supported heat pump");
        }

        return new HeatPumpStateResponse(acData.getAcType(), currentState, acData.getLastPolledStateHex());
    }

    @PostMapping("/heat-pump/ac-devices")
    public List<HeatPumpAcDeviceResponse> listSelectableHeatPumpAcDevices(
            @RequestBody HeatPumpAcDevicesRequest request
    ) {
        authContext.getAccountId();
        return heatPumpAcDeviceSelectionService.getSelectableDevices(request);
    }

    public record HeatPumpStateResponse(
            AcType acType,
            String currentState,
            String lastPolledState
    ) {
    }
}
