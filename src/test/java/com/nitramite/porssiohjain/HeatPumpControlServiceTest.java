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

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceHeatPumpEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.WeatherControlEntity;
import com.nitramite.porssiohjain.entity.WeatherControlHeatPumpEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.WeatherMetricType;
import com.nitramite.porssiohjain.entity.repository.ControlHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlHeatPumpRepository;
import com.nitramite.porssiohjain.services.AcCommandDispatchService;
import com.nitramite.porssiohjain.services.HeatPumpControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeatPumpControlServiceTest {

    @Mock
    private WeatherControlHeatPumpRepository weatherControlHeatPumpRepository;

    @Mock
    private ProductionSourceHeatPumpRepository productionSourceHeatPumpRepository;

    @Mock
    private ControlHeatPumpRepository controlHeatPumpRepository;

    @Mock
    private DeviceAcDataRepository deviceAcDataRepository;

    @Mock
    private SiteWeatherRepository siteWeatherRepository;

    @Mock
    private NordpoolRepository nordpoolRepository;

    @Mock
    private ControlTableRepository controlTableRepository;

    @Mock
    private AcCommandDispatchService acCommandDispatchService;

    private HeatPumpControlService heatPumpControlService;

    @BeforeEach
    void setUp() {
        heatPumpControlService = new HeatPumpControlService(
                weatherControlHeatPumpRepository,
                productionSourceHeatPumpRepository,
                controlHeatPumpRepository,
                deviceAcDataRepository,
                siteWeatherRepository,
                nordpoolRepository,
                controlTableRepository,
                acCommandDispatchService
        );
    }

    @Test
    void weatherRuleHasPriorityOverProductionRule() {
        DeviceEntity device = enabledHeatPumpDevice(1L);
        DeviceAcDataEntity acData = acData(device, "BBBB");
        SiteEntity site = new SiteEntity();

        WeatherControlEntity weatherControl = new WeatherControlEntity();
        weatherControl.setSite(site);

        WeatherControlHeatPumpEntity weatherRule = WeatherControlHeatPumpEntity.builder()
                .id(10L)
                .device(device)
                .weatherControl(weatherControl)
                .stateHex("AAAA")
                .weatherMetric(WeatherMetricType.TEMPERATURE)
                .comparisonType(ComparisonType.LESS_THAN)
                .thresholdValue(BigDecimal.valueOf(5))
                .build();

        ProductionSourceEntity productionSource = new ProductionSourceEntity();
        productionSource.setEnabled(true);
        productionSource.setCurrentKw(BigDecimal.valueOf(8));

        ProductionSourceHeatPumpEntity productionRule = ProductionSourceHeatPumpEntity.builder()
                .id(20L)
                .device(device)
                .productionSource(productionSource)
                .stateHex("CCCC")
                .controlAction(ControlAction.TURN_ON)
                .comparisonType(ComparisonType.GREATER_THAN)
                .triggerKw(BigDecimal.valueOf(3))
                .build();

        SiteWeatherEntity siteWeather = new SiteWeatherEntity();
        siteWeather.setTemperature(BigDecimal.ZERO);

        when(weatherControlHeatPumpRepository.findAll()).thenReturn(List.of(weatherRule));
        when(productionSourceHeatPumpRepository.findAll()).thenReturn(List.of(productionRule));
        when(controlHeatPumpRepository.findAll()).thenReturn(List.of());
        when(siteWeatherRepository.findFirstBySiteAndForecastTimeLessThanEqualOrderByForecastTimeDesc(any(SiteEntity.class), any()))
                .thenReturn(Optional.empty());
        when(siteWeatherRepository.findFirstBySiteAndForecastTimeGreaterThanEqualOrderByForecastTimeAsc(any(SiteEntity.class), any()))
                .thenReturn(Optional.of(siteWeather));
        when(deviceAcDataRepository.findByDevice(device)).thenReturn(Optional.of(acData));

        heatPumpControlService.runScheduledHeatPumpControls();

        verify(acCommandDispatchService).dispatchHexState(acData, "AAAA");
    }

    @Test
    void skipsDispatchWhenDesiredStateMatchesLastPolledHex() {
        DeviceEntity device = enabledHeatPumpDevice(1L);
        DeviceAcDataEntity acData = acData(device, "AAAA");
        SiteEntity site = new SiteEntity();

        WeatherControlEntity weatherControl = new WeatherControlEntity();
        weatherControl.setSite(site);

        WeatherControlHeatPumpEntity weatherRule = WeatherControlHeatPumpEntity.builder()
                .id(10L)
                .device(device)
                .weatherControl(weatherControl)
                .stateHex("AAAA")
                .weatherMetric(WeatherMetricType.TEMPERATURE)
                .comparisonType(ComparisonType.LESS_THAN)
                .thresholdValue(BigDecimal.valueOf(5))
                .build();

        SiteWeatherEntity siteWeather = new SiteWeatherEntity();
        siteWeather.setTemperature(BigDecimal.ZERO);

        when(weatherControlHeatPumpRepository.findAll()).thenReturn(List.of(weatherRule));
        when(productionSourceHeatPumpRepository.findAll()).thenReturn(List.of());
        when(controlHeatPumpRepository.findAll()).thenReturn(List.of());
        when(siteWeatherRepository.findFirstBySiteAndForecastTimeLessThanEqualOrderByForecastTimeDesc(any(SiteEntity.class), any()))
                .thenReturn(Optional.empty());
        when(siteWeatherRepository.findFirstBySiteAndForecastTimeGreaterThanEqualOrderByForecastTimeAsc(any(SiteEntity.class), any()))
                .thenReturn(Optional.of(siteWeather));
        when(deviceAcDataRepository.findByDevice(device)).thenReturn(Optional.of(acData));

        heatPumpControlService.runScheduledHeatPumpControls();

        verify(acCommandDispatchService, never()).dispatchHexState(acData, "AAAA");
    }

    private DeviceEntity enabledHeatPumpDevice(Long id) {
        DeviceEntity device = new DeviceEntity();
        device.setId(id);
        device.setEnabled(true);
        device.setDeviceType(DeviceType.HEAT_PUMP);
        return device;
    }

    private DeviceAcDataEntity acData(DeviceEntity device, String lastPolledStateHex) {
        DeviceAcDataEntity acData = new DeviceAcDataEntity();
        acData.setId(100L);
        acData.setDevice(device);
        acData.setAcType(AcType.TOSHIBA);
        acData.setLastPolledStateHex(lastPolledStateHex);
        return acData;
    }

}
