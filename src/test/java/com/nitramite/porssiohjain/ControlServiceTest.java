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

package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.ControlDeviceEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.WeatherControlDeviceEntity;
import com.nitramite.porssiohjain.entity.WeatherControlEntity;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.ControlMode;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.WeatherMetricType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ControlDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ControlHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ResourceSharingRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlDeviceRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.PowerLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlServiceTest {

    @Mock
    private ControlRepository controlRepository;

    @Mock
    private ControlDeviceRepository controlDeviceRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private ControlTableRepository controlTableRepository;

    @Mock
    private PowerLimitDeviceRepository powerLimitDeviceRepository;

    @Mock
    private ElectricityContractRepository electricityContractRepository;

    @Mock
    private PowerLimitService powerLimitService;

    @Mock
    private ProductionSourceDeviceRepository productionSourceDeviceRepository;

    @Mock
    private WeatherControlDeviceRepository weatherControlDeviceRepository;

    @Mock
    private SiteWeatherRepository siteWeatherRepository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private ResourceSharingRepository resourceSharingRepository;

    @Mock
    private ControlHeatPumpRepository controlHeatPumpRepository;

    @Mock
    private MqttService mqttService;

    @Mock
    private AccountLimitService accountLimitService;

    private ControlService controlService;

    @BeforeEach
    void setUp() {
        controlService = new ControlService(
                controlRepository,
                controlDeviceRepository,
                accountRepository,
                deviceRepository,
                controlTableRepository,
                powerLimitDeviceRepository,
                electricityContractRepository,
                powerLimitService,
                productionSourceDeviceRepository,
                weatherControlDeviceRepository,
                siteWeatherRepository,
                siteRepository,
                resourceSharingRepository,
                controlHeatPumpRepository,
                mqttService,
                accountLimitService
        );
    }

    @Test
    void weatherPriorityRuleOverridesControlRuleForStandardDevice() {
        UUID deviceUuid = UUID.randomUUID();
        DeviceEntity device = new DeviceEntity();
        device.setId(1L);
        device.setUuid(deviceUuid);
        device.setEnabled(true);
        device.setDeviceType(DeviceType.STANDARD);

        SiteEntity site = new SiteEntity();
        site.setId(10L);

        WeatherControlEntity weatherControl = new WeatherControlEntity();
        weatherControl.setSite(site);

        WeatherControlDeviceEntity weatherRule = WeatherControlDeviceEntity.builder()
                .id(100L)
                .weatherControl(weatherControl)
                .device(device)
                .deviceChannel(1)
                .weatherMetric(WeatherMetricType.TEMPERATURE)
                .comparisonType(ComparisonType.LESS_THAN)
                .thresholdValue(BigDecimal.valueOf(5))
                .controlAction(ControlAction.TURN_OFF)
                .priorityRule(true)
                .build();

        ControlEntity control = new ControlEntity();
        control.setId(200L);
        control.setMode(ControlMode.MANUAL);
        control.setManualOn(true);
        control.setTimezone("Europe/Helsinki");

        ControlDeviceEntity controlDevice = new ControlDeviceEntity();
        controlDevice.setId(300L);
        controlDevice.setDevice(device);
        controlDevice.setDeviceChannel(1);
        controlDevice.setControl(control);

        SiteWeatherEntity siteWeather = new SiteWeatherEntity();
        siteWeather.setTemperature(BigDecimal.ZERO);

        when(deviceRepository.findByUuid(deviceUuid)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(DeviceEntity.class))).thenReturn(device);
        when(powerLimitDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(productionSourceDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(weatherControlDeviceRepository.findByDevice(device)).thenReturn(List.of(weatherRule));
        when(controlDeviceRepository.findByDevice(device)).thenReturn(List.of(controlDevice));
        when(siteWeatherRepository.findFirstBySiteAndForecastTimeLessThanEqualOrderByForecastTimeDesc(any(SiteEntity.class), any()))
                .thenReturn(Optional.empty());
        when(siteWeatherRepository.findFirstBySiteAndForecastTimeGreaterThanEqualOrderByForecastTimeAsc(any(SiteEntity.class), any()))
                .thenReturn(Optional.of(siteWeather));

        Map<Integer, Integer> controls = controlService.getControlsForDevice(deviceUuid.toString());

        assertEquals(Map.of(1, 0), controls);
        verify(controlDeviceRepository).findByDevice(device);
    }
}
