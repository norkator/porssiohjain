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
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.ZigbeeGatewaySyncService;
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
    ZigbeeGatewaySyncService service;
    AccountEntity account;
    UUID gateway;

    @BeforeEach void setUp() {
        service = new ZigbeeGatewaySyncService(accounts, devices, links);
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
        ArgumentCaptor<DeviceEntity> captured = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(devices).save(captured.capture());
        assertEquals(account, captured.getValue().getAccount());
        assertEquals("ANDROID_ZIGBEE", captured.getValue().getDevicePlatform().name());
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
        return ZigbeeGatewayDeviceEntity.builder().account(owner).gatewayId(gateway)
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
