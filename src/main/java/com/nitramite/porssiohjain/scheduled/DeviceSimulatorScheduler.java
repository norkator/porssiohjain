/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.services.simulation.DeviceSimulatorProperties;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceSimulatorScheduler {
    private final DeviceSimulatorProperties properties;
    private final DeviceRepository devices;
    private final DeviceAcDataRepository acData;
    private final ToshibaAcStateService states;

    @Scheduled(fixedDelayString = "${app.device-simulator.refresh-interval:1m}")
    @Transactional
    public void refreshStates() {
        if (properties.getBaseUrl().isBlank() || properties.getToken().isBlank()) return;
        for (var device : devices.findBySimulatedTrueAndEnabledTrueAndAccountDemoTrueOrderByIdAsc()) {
            if (device.getDeviceType() != DeviceType.HEAT_PUMP) continue;
            acData.findByDevice(device).filter(data -> data.getAcType() == AcType.TOSHIBA).ifPresent(data -> {
                try {
                    states.getAcState(data);
                } catch (RuntimeException exception) {
                    log.warn("Simulated heat pump unavailable. deviceId={}", device.getId());
                }
            });
        }
    }
}
