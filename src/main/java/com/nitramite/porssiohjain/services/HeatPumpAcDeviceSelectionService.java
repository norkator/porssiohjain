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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcDevicesService;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiLoginService;
import com.nitramite.porssiohjain.services.models.HeatPumpAcDeviceResponse;
import com.nitramite.porssiohjain.services.models.HeatPumpAcDevicesRequest;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcDevicesService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HeatPumpAcDeviceSelectionService {

    private final ToshibaLoginService toshibaLoginService;
    private final ToshibaAcDevicesService toshibaAcDevicesService;
    private final MitsubishiLoginService mitsubishiLoginService;
    private final MitsubishiAcDevicesService mitsubishiAcDevicesService;

    public List<HeatPumpAcDeviceResponse> getSelectableDevices(HeatPumpAcDevicesRequest request) {
        validateRequest(request);
        DeviceAcDataEntity acData = buildAcData(request);

        return switch (request.getAcType()) {
            case TOSHIBA -> getToshibaDevices(acData);
            case MITSUBISHI -> getMitsubishiDevices(acData);
            case NONE -> throw new IllegalArgumentException("AC type is required");
        };
    }

    private List<HeatPumpAcDeviceResponse> getToshibaDevices(DeviceAcDataEntity acData) {
        if (!toshibaLoginService.login(acData).isSuccess()) {
            throw new IllegalStateException("Toshiba login failed");
        }

        return toshibaAcDevicesService.getAcDevices(acData).stream()
                .map(device -> HeatPumpAcDeviceResponse.builder()
                        .acType(AcType.TOSHIBA)
                        .id(device.getId())
                        .name(device.getName())
                        .deviceUniqueId(device.getDeviceUniqueId())
                        .build())
                .toList();
    }

    private List<HeatPumpAcDeviceResponse> getMitsubishiDevices(DeviceAcDataEntity acData) {
        if (!mitsubishiLoginService.login(acData).isSuccess()) {
            throw new IllegalStateException("Mitsubishi login failed");
        }

        return mitsubishiAcDevicesService.getAcDevices(acData).stream()
                .map(device -> HeatPumpAcDeviceResponse.builder()
                        .acType(AcType.MITSUBISHI)
                        .id(String.valueOf(device.getDeviceId()))
                        .name(device.getDeviceName())
                        .buildingId(device.getBuildingId() != null ? String.valueOf(device.getBuildingId()) : null)
                        .build())
                .toList();
    }

    private DeviceAcDataEntity buildAcData(HeatPumpAcDevicesRequest request) {
        return DeviceAcDataEntity.builder()
                .name(request.getHpName() != null && !request.getHpName().isBlank() ? request.getHpName() : "Heat pump")
                .acType(request.getAcType())
                .acUsername(request.getAcUsername())
                .acPassword(request.getAcPassword())
                .build();
    }

    private void validateRequest(HeatPumpAcDevicesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (request.getAcType() == null || request.getAcType() == AcType.NONE) {
            throw new IllegalArgumentException("AC type is required");
        }
        if (request.getAcUsername() == null || request.getAcUsername().isBlank()) {
            throw new IllegalArgumentException("AC username is required");
        }
        if (request.getAcPassword() == null || request.getAcPassword().isBlank()) {
            throw new IllegalArgumentException("AC password is required");
        }
    }
}
