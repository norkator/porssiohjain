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

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.ZigbeeGatewaySyncService;
import com.nitramite.porssiohjain.services.ZigbeeGatewayConnectivityService;
import com.nitramite.porssiohjain.services.DeviceOfflineNotificationService;
import com.nitramite.porssiohjain.services.models.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZigbeeGatewaySyncServiceTest {
    @Mock AccountRepository accounts;
    @Mock DeviceRepository devices;
    @Mock ZigbeeGatewayDeviceRepository links;
    @Mock ZigbeeDeviceMeasurementRepository measurements;
    @Mock ZigbeeGatewayConnectivityService connectivityService;
    @Mock DeviceOfflineNotificationService deviceOfflineNotificationService;
    ZigbeeGatewaySyncService service;
    AccountEntity account;
    UUID gateway;

    @BeforeEach void setUp() {
        service = new ZigbeeGatewaySyncService(
                accounts, devices, links, measurements, connectivityService, deviceOfflineNotificationService);
        account = new AccountEntity(); account.setId(7L);
        gateway = UUID.randomUUID();
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
    }

    @Test void firstSyncRegistersAccountOwnedThermostat() {
        when(links.findByGatewayIdAndZigbeeIeee(gateway, "8c6fb9fffe2d5cdb")).thenReturn(Optional.empty());
        when(devices.save(any())).thenAnswer(call -> { DeviceEntity d = call.getArgument(0); d.setId(11L); return d; });
        when(links.save(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.sync(7L, gateway, request(0, null));
        assertTrue(response.getDevices().isEmpty());
        verify(connectivityService).recordHeartbeat(eq(account), eq(gateway), any());
        ArgumentCaptor<DeviceEntity> captured = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(devices, times(2)).save(captured.capture());
        DeviceEntity registered = captured.getAllValues().getLast();
        assertEquals(account, registered.getAccount());
        assertEquals("ANDROID_ZIGBEE", registered.getDevicePlatform().name());
        assertEquals(DeviceType.THERMOSTAT, registered.getDeviceType());
        assertTrue(registered.isApiOnline());
        assertNotNull(registered.getLastCommunication());
        verify(deviceOfflineNotificationService).sendIfDeviceCameOnline(
                eq(registered), eq(false), eq(false), eq("API"), eq(registered.getLastCommunication()));
    }

    @Test void sensorSyncRegistersTemperatureSensorAndStoresMeasurements() {
        when(links.findByGatewayIdAndZigbeeIeee(gateway, "00158d000abc1234")).thenReturn(Optional.empty());
        when(devices.save(any())).thenAnswer(call -> { DeviceEntity d = call.getArgument(0); d.setId(12L); return d; });
        when(links.save(any())).thenAnswer(call -> call.getArgument(0));
        ZigbeeGatewaySyncRequest.DeviceReport report = new ZigbeeGatewaySyncRequest.DeviceReport();
        report.setZigbeeIeee("0x00158D000ABC1234");
        report.setProfile("TS0201");
        report.setCustomName("Bedroom sensor");
        report.setTemperature(new BigDecimal("21.75"));
        report.setHumidity(new BigDecimal("38.50"));
        Instant measuredAt = Instant.now().minusSeconds(30);
        report.setMeasuredAt(measuredAt);
        ZigbeeGatewaySyncRequest request = new ZigbeeGatewaySyncRequest();
        request.setGatewayId(gateway);
        request.setDevices(List.of(report));

        var response = service.sync(7L, gateway, request);

        assertTrue(response.getDevices().isEmpty());
        ArgumentCaptor<DeviceEntity> deviceCaptor = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(devices, times(2)).save(deviceCaptor.capture());
        DeviceEntity registered = deviceCaptor.getAllValues().getLast();
        assertEquals(DeviceType.TEMPERATURE_SENSOR, registered.getDeviceType());
        assertEquals("Bedroom sensor", registered.getDeviceName());
        ArgumentCaptor<ZigbeeDeviceMeasurementEntity> measurementCaptor =
                ArgumentCaptor.forClass(ZigbeeDeviceMeasurementEntity.class);
        verify(measurements, times(2)).save(measurementCaptor.capture());
        List<ZigbeeDeviceMeasurementEntity> saved = measurementCaptor.getAllValues();
        assertEquals(ZigbeeMeasurementType.TEMPERATURE, saved.get(0).getMeasurementType());
        assertEquals(new BigDecimal("21.75"), saved.get(0).getValue());
        assertEquals(measuredAt, saved.get(0).getMeasuredAt());
        assertEquals(ZigbeeMeasurementType.HUMIDITY, saved.get(1).getMeasurementType());
        assertEquals(new BigDecimal("38.50"), saved.get(1).getValue());
    }

    @Test void thermostatReportMarksExistingDeviceApiOnline() {
        DeviceEntity device = DeviceEntity.builder()
                .id(11L).account(account).deviceName("Hall thermostat").timezone("Europe/Helsinki")
                .apiOnline(false).mqttOnline(false).build();
        ZigbeeGatewayDeviceEntity link = link(account, 0, 0);
        link.setDevice(device);
        when(links.findByGatewayIdAndZigbeeIeee(gateway, "8c6fb9fffe2d5cdb"))
                .thenReturn(Optional.of(link));

        service.sync(7L, gateway, request(0, null));

        assertTrue(device.isApiOnline());
        assertNotNull(device.getLastCommunication());
        verify(devices).save(device);
        verify(deviceOfflineNotificationService).sendIfDeviceCameOnline(
                eq(device), eq(false), eq(false), eq("API"), eq(device.getLastCommunication()));
    }

    @Test void repeatsDesiredVersionUntilSuccessfulAcknowledgement() {
        ZigbeeGatewayDeviceEntity link = link(account, 3, 0);
        link.setDesiredTemperature(new BigDecimal("20.50")); link.setDesiredMode("HEAT");
        link.setDesiredExpiresAt(Instant.now().plusSeconds(600));
        when(links.findByGatewayIdAndZigbeeIeee(gateway, "8c6fb9fffe2d5cdb")).thenReturn(Optional.of(link));
        assertEquals(3, service.sync(7L, gateway, request(0, null)).getDevices().getFirst().getVersion());
        service.sync(7L, gateway, request(3, true));
        assertEquals(3, link.getAppliedVersion());
        assertTrue(service.sync(7L, gateway, request(3, true)).getDevices().isEmpty());
    }

    @Test void resendsDesiredVersionWhenReportedSetpointDriftsAfterManualChange() {
        ZigbeeGatewayDeviceEntity link = link(account, 4, 4);
        link.setDesiredTemperature(new BigDecimal("19.00")); link.setDesiredMode("HEAT");
        link.setDesiredExpiresAt(Instant.now().plusSeconds(600));
        when(links.findByGatewayIdAndZigbeeIeee(gateway, "8c6fb9fffe2d5cdb")).thenReturn(Optional.of(link));

        ZigbeeGatewaySyncRequest request = request(4, true);
        request.getDevices().getFirst().setSetpoint(new BigDecimal("10.00"));
        request.getDevices().getFirst().setMode("HEAT");

        var response = service.sync(7L, gateway, request);

        assertEquals(5, response.getDevices().getFirst().getVersion());
        assertEquals(5, link.getDesiredVersion());
        assertEquals(4, link.getAppliedVersion());
    }

    @Test void rejectsCrossAccountGatewayLink() {
        AccountEntity other = new AccountEntity(); other.setId(8L);
        when(links.findByGatewayIdAndZigbeeIeee(gateway, "8c6fb9fffe2d5cdb"))
                .thenReturn(Optional.of(link(other, 0, 0)));
        assertThrows(ResponseStatusException.class, () -> service.sync(7L, gateway, request(0, null)));
    }

    @Test void rejectsVersionBeyondDesiredAndMismatchedGateway() {
        when(links.findByGatewayIdAndZigbeeIeee(gateway, "8c6fb9fffe2d5cdb"))
                .thenReturn(Optional.of(link(account, 2, 0)));
        assertThrows(ResponseStatusException.class, () -> service.sync(7L, gateway, request(3, true)));
        assertThrows(ResponseStatusException.class, () -> service.sync(7L, UUID.randomUUID(), request(0, null)));
    }

    private ZigbeeGatewayDeviceEntity link(AccountEntity owner, long desired, long applied) {
        DeviceEntity device = DeviceEntity.builder()
                .id(11L).account(owner).deviceName("Thermostat").timezone("Europe/Helsinki").build();
        return ZigbeeGatewayDeviceEntity.builder().account(owner).device(device).gatewayId(gateway)
                .zigbeeIeee("8c6fb9fffe2d5cdb").profile("schneider_wde002497")
                .desiredVersion(desired).appliedVersion(applied).build();
    }

    private ZigbeeGatewaySyncRequest request(long version, Boolean success) {
        ZigbeeGatewaySyncRequest.DeviceReport report = new ZigbeeGatewaySyncRequest.DeviceReport();
        report.setZigbeeIeee("0x8C6FB9FFFE2D5CDB"); report.setProfile("schneider_wde002497");
        report.setLastAppliedVersion(version); report.setSuccess(success);
        ZigbeeGatewaySyncRequest request = new ZigbeeGatewaySyncRequest();
        request.setGatewayId(gateway); request.setDevices(List.of(report)); return request;
    }
}
