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

package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ControlDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ControlHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlThermostatRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.LoadSheddingLinkRepository;
import com.nitramite.porssiohjain.entity.repository.LoadSheddingNodeRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.ResourceSharingRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlHeatPumpRepository;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ControlRepository controlRepository;
    @Mock
    private PowerLimitRepository powerLimitRepository;
    @Mock
    private ResourceSharingRepository resourceSharingRepository;
    @Mock
    private DeviceAcDataRepository deviceAcDataRepository;
    @Mock
    private ControlDeviceRepository controlDeviceRepository;
    @Mock
    private PowerLimitDeviceRepository powerLimitDeviceRepository;
    @Mock
    private ProductionSourceDeviceRepository productionSourceDeviceRepository;
    @Mock
    private WeatherControlDeviceRepository weatherControlDeviceRepository;
    @Mock
    private LoadSheddingNodeRepository loadSheddingNodeRepository;
    @Mock
    private LoadSheddingLinkRepository loadSheddingLinkRepository;
    @Mock
    private ControlHeatPumpRepository controlHeatPumpRepository;
    @Mock
    private ProductionSourceHeatPumpRepository productionSourceHeatPumpRepository;
    @Mock
    private WeatherControlHeatPumpRepository weatherControlHeatPumpRepository;
    @Mock
    private ControlThermostatRepository controlThermostatRepository;
    @Mock
    private AccountLimitService accountLimitService;
    @Mock
    private ControlService controlService;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(
                deviceRepository,
                accountRepository,
                controlRepository,
                powerLimitRepository,
                resourceSharingRepository,
                deviceAcDataRepository,
                controlDeviceRepository,
                powerLimitDeviceRepository,
                productionSourceDeviceRepository,
                weatherControlDeviceRepository,
                loadSheddingNodeRepository,
                loadSheddingLinkRepository,
                controlHeatPumpRepository,
                productionSourceHeatPumpRepository,
                weatherControlHeatPumpRepository,
                controlThermostatRepository,
                accountLimitService,
                controlService
        );
    }

    @Test
    void keepsHeatPumpApiOnlineWithinFourHours() {
        DeviceEntity device = new DeviceEntity();
        device.setId(1L);
        device.setDeviceType(DeviceType.HEAT_PUMP);
        device.setApiOnline(true);
        device.setLastCommunication(Instant.now().minusSeconds(3 * 60 * 60));

        when(deviceRepository.findByApiOnlineTrue()).thenReturn(List.of(device));
        when(deviceRepository.findByMqttOnlineTrueAndLastCommunicationBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        deviceService.checkOfflineDevices();

        verify(deviceRepository, never()).save(device);
    }

    @Test
    void marksHeatPumpApiOfflineAfterFourHours() {
        DeviceEntity device = new DeviceEntity();
        device.setId(2L);
        device.setDeviceType(DeviceType.HEAT_PUMP);
        device.setApiOnline(true);
        device.setLastCommunication(Instant.now().minusSeconds(4 * 60 * 60 + 60));

        when(deviceRepository.findByApiOnlineTrue()).thenReturn(List.of(device));
        when(deviceRepository.findByMqttOnlineTrueAndLastCommunicationBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        deviceService.checkOfflineDevices();

        verify(deviceRepository).save(device);
    }
}
