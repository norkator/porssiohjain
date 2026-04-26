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
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeatPumpOnlineCheckService {

    private static final Duration ONLINE_THRESHOLD = Duration.ofHours(4);

    private final DeviceAcDataRepository deviceAcDataRepository;
    private final DeviceRepository deviceRepository;
    private final ToshibaAcStateService toshibaAcStateService;

    @Transactional
    public void refreshHeatPumpApiOnlineStates() {
        List<DeviceAcDataEntity> heatPumpAcData = deviceAcDataRepository.findByDeviceDeviceType(DeviceType.HEAT_PUMP);
        for (DeviceAcDataEntity acData : heatPumpAcData) {
            try {
                refreshHeatPumpApiOnlineState(acData);
            } catch (Exception e) {
                DeviceEntity device = acData.getDevice();
                log.error("Failed to refresh heat pump online state for deviceId={}", device != null ? device.getId() : null, e);
            }
        }
    }

    private void refreshHeatPumpApiOnlineState(DeviceAcDataEntity acData) {
        DeviceEntity device = acData.getDevice();
        if (device == null || !device.isEnabled()) {
            return;
        }
        AcType acType = acData.getAcType();
        if (acType == null || acType == AcType.NONE) {
            return;
        }

        switch (acType) {
            case TOSHIBA -> toshibaAcStateService.getAcState(acData);
            case MITSUBISHI -> refreshMitsubishiOnlineStateMock(device);
            default -> {
            }
        }
    }

    private void refreshMitsubishiOnlineStateMock(DeviceEntity device) {
        if (device.getId() == null) {
            return;
        }
        DeviceEntity managedDevice = deviceRepository.findById(device.getId()).orElse(null);
        if (managedDevice == null) {
            return;
        }
        managedDevice.setApiOnline(isWithinOnlineThreshold(managedDevice.getLastCommunication()));
        deviceRepository.save(managedDevice);
        log.debug("Mitsubishi online check mock applied for deviceId={}", managedDevice.getId());
    }

    private boolean isWithinOnlineThreshold(Instant lastCommunication) {
        return lastCommunication != null
                && Duration.between(lastCommunication, Instant.now()).compareTo(ONLINE_THRESHOLD) < 0;
    }
}
