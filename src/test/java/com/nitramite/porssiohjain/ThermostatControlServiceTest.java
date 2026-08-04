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

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlThermostatEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.ZigbeeGatewayDeviceEntity;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.ControlThermostatRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeGatewayDeviceRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.ControlPriceService;
import com.nitramite.porssiohjain.services.ThermostatControlService;
import com.nitramite.porssiohjain.services.ThermostatCurveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThermostatControlServiceTest {

    @Mock
    private ControlThermostatRepository controlThermostatRepository;

    @Mock
    private ControlPriceService controlPriceService;

    @Mock
    private ThermostatCurveService thermostatCurveService;

    @Mock
    private MqttService mqttService;

    @Mock
    private ZigbeeGatewayDeviceRepository zigbeeGatewayDeviceRepository;

    private ThermostatControlService thermostatControlService;

    @BeforeEach
    void setUp() {
        thermostatControlService = new ThermostatControlService(
                controlThermostatRepository,
                controlPriceService,
                thermostatCurveService,
                mqttService,
                zigbeeGatewayDeviceRepository
        );
    }

    @Test
    void sendsThermostatTargetTemperatureForEligibleRule() {
        DeviceEntity device = thermostatDevice(1L, true, true);
        ControlEntity control = new ControlEntity();
        control.setId(10L);
        ControlThermostatEntity rule = ControlThermostatEntity.builder()
                .id(20L)
                .control(control)
                .device(device)
                .thermostatChannel(2)
                .curveJson("[]")
                .enabled(true)
                .build();

        when(controlThermostatRepository.findAll()).thenReturn(List.of(rule));
        when(controlPriceService.getCurrentCombinedPrice(any(), any())).thenReturn(Optional.of(BigDecimal.valueOf(8)));
        when(thermostatCurveService.evaluate("[]", BigDecimal.valueOf(8))).thenReturn(BigDecimal.valueOf(21.5));
        when(controlThermostatRepository.save(any(ControlThermostatEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        thermostatControlService.runScheduledThermostatControls();

        verify(mqttService).setThermostatTemperature(device.getUuid().toString(), 2, BigDecimal.valueOf(21.5));
        verify(controlThermostatRepository).save(rule);
    }

    @Test
    void skipsThermostatRuleWhenRecentlyAppliedWithoutMeaningfulChange() {
        DeviceEntity device = thermostatDevice(2L, true, true);
        ControlEntity control = new ControlEntity();
        control.setId(11L);
        ControlThermostatEntity rule = ControlThermostatEntity.builder()
                .id(21L)
                .control(control)
                .device(device)
                .thermostatChannel(1)
                .curveJson("[]")
                .enabled(true)
                .lastAppliedTemperature(new BigDecimal("21.20"))
                .lastAppliedAt(Instant.now())
                .build();

        when(controlThermostatRepository.findAll()).thenReturn(List.of(rule));
        when(controlPriceService.getCurrentCombinedPrice(any(), any())).thenReturn(Optional.of(BigDecimal.valueOf(5)));
        when(thermostatCurveService.evaluate("[]", BigDecimal.valueOf(5))).thenReturn(new BigDecimal("21.40"));

        thermostatControlService.runScheduledThermostatControls();

        verify(mqttService, never()).setThermostatTemperature(any(), any(Integer.class), any());
        verify(controlThermostatRepository, never()).save(any(ControlThermostatEntity.class));
    }

    @Test
    void storesVersionedDesiredStateForAndroidZigbeeWithoutPublishingMqtt() {
        DeviceEntity device = thermostatDevice(3L, true, false);
        device.setDevicePlatform(DevicePlatform.ANDROID_ZIGBEE);
        ControlEntity control = new ControlEntity(); control.setId(12L);
        ControlThermostatEntity rule = ControlThermostatEntity.builder().id(22L).control(control)
                .device(device).thermostatChannel(1).curveJson("[]").enabled(true).build();
        ZigbeeGatewayDeviceEntity link = ZigbeeGatewayDeviceEntity.builder().device(device).desiredVersion(4).build();
        when(controlThermostatRepository.findAll()).thenReturn(List.of(rule));
        when(controlPriceService.getCurrentCombinedPrice(any(), any())).thenReturn(Optional.of(BigDecimal.ONE));
        when(thermostatCurveService.evaluate("[]", BigDecimal.ONE)).thenReturn(new BigDecimal("20.50"));
        when(zigbeeGatewayDeviceRepository.findByDeviceId(3L)).thenReturn(Optional.of(link));

        thermostatControlService.runScheduledThermostatControls();

        assertEquals(5, link.getDesiredVersion());
        assertEquals(new BigDecimal("20.50"), link.getDesiredTemperature());
        verify(zigbeeGatewayDeviceRepository).save(link);
        verify(mqttService, never()).setThermostatTemperature(any(), any(Integer.class), any());
    }

    private DeviceEntity thermostatDevice(Long id, boolean enabled, boolean mqttOnline) {
        DeviceEntity device = new DeviceEntity();
        device.setId(id);
        device.setUuid(UUID.randomUUID());
        device.setDeviceType(DeviceType.THERMOSTAT);
        device.setEnabled(enabled);
        device.setMqttOnline(mqttOnline);
        return device;
    }
}
