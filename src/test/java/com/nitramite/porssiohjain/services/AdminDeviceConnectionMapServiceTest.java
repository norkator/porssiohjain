package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.ZigbeeGatewayStatusEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeGatewayStatusRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminDeviceConnectionMapServiceTest {
    private final AdminClientCallLogService calls = new AdminClientCallLogService();
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final ZigbeeGatewayStatusRepository gateways = mock(ZigbeeGatewayStatusRepository.class);
    private final AdminDeviceConnectionMapService service = new AdminDeviceConnectionMapService(calls, devices, gateways);
    private final AccountEntity owner = AccountEntity.builder().id(7L).email("owner@example.invalid").build();

    @Test
    void includesEveryOnlineDeviceTypeWithoutIncomingHttpCallsAndCountsBothTransportsOnce() {
        var pump = device(DeviceType.HEAT_PUMP, true, false);
        var thermostat = device(DeviceType.THERMOSTAT, false, true);
        var sensor = device(DeviceType.TEMPERATURE_SENSOR, true, true);
        var relay = device(DeviceType.STANDARD, true, false);
        when(devices.findConnectionMapDevices(List.of())).thenReturn(List.of(pump, thermostat, sensor, relay));

        var clients = service.findClients();
        assertEquals(List.of("HEAT_PUMP", "THERMOSTAT", "TEMPERATURE_SENSOR", "CONTROL_DEVICE"),
                clients.stream().map(AdminDeviceConnectionMapService.MapClient::type).toList());
        assertTrue(clients.stream().allMatch(client -> Boolean.TRUE.equals(client.online())));
        assertTrue(clients.stream().allMatch(client -> client.accountId().equals(7L)));
        assertTrue(clients.stream().allMatch(client -> client.calledAt() == null && client.ip() == null && client.endpoint() == null));
        assertEquals("API + MQTT", clients.get(2).connection());
        assertEquals(pump.getLastCommunication(), clients.getFirst().lastCommunication());
        assertEquals("owner@example.invalid", clients.getFirst().accountEmail());
    }

    @Test
    void mergesCallsWithKnownDevicesAndKeepsOwnershipSeparateDespiteSharedIp() {
        var pump = device(DeviceType.HEAT_PUMP, true, true);
        var offlineRelay = device(DeviceType.STANDARD, false, false);
        offlineRelay.setAccount(AccountEntity.builder().id(8L).build());
        calls.recordControlDeviceCall(pump.getUuid().toString().toUpperCase(), "192.0.2.1", "/control/{deviceUuid}");
        calls.recordControlDeviceCall(offlineRelay.getUuid().toString(), "192.0.2.1", "/control/{deviceUuid}/timetable");
        when(devices.findConnectionMapDevices(anyCollection())).thenReturn(List.of(pump, offlineRelay));

        var clients = service.findClients();
        assertEquals(2, clients.size());
        assertEquals("HEAT_PUMP", clients.getFirst().type());
        assertEquals("192.0.2.1", clients.getFirst().ip());
        assertNotNull(clients.getFirst().calledAt());
        assertEquals(pump.getLastCommunication(), clients.getFirst().lastCommunication());
        assertEquals(7L, clients.getFirst().accountId());
        assertEquals(8L, clients.getLast().accountId());
        assertFalse(clients.getLast().online());
        assertEquals("/control/{deviceUuid}/timetable", clients.getLast().endpoint());
        verify(devices).findConnectionMapDevices(argThat(ids -> ids.size() == 2 && ids.contains(pump.getUuid()) && ids.contains(offlineRelay.getUuid())));
    }

    @Test
    void equivalentUuidSpellingsKeepTheMostRecentCallDetails() {
        var pump = device(DeviceType.HEAT_PUMP, true, false);
        calls.recordControlDeviceCall(pump.getUuid().toString().toUpperCase(), "192.0.2.1", "/control/{deviceUuid}");
        calls.recordControlDeviceCall(pump.getUuid().toString(), "192.0.2.2", "/control/{deviceUuid}/timetable");
        when(devices.findConnectionMapDevices(anyCollection())).thenReturn(List.of(pump));

        var clients = service.findClients();
        assertEquals(1, clients.size());
        assertEquals("192.0.2.2", clients.getFirst().ip());
        assertEquals("/control/{deviceUuid}/timetable", clients.getFirst().endpoint());
    }

    @Test
    void gatewayOnlineStateUsesHeartbeatTimeoutRatherThanTwoMinuteCallRecency() {
        var gatewayId = UUID.randomUUID();
        var staleId = UUID.randomUUID();
        var heartbeat = Instant.now().minusSeconds(300);
        calls.recordZigbeeGatewaySync(staleId.toString(), "192.0.2.2");
        when(gateways.findConnectionMapGateways(anyCollection(), any())).thenReturn(List.of(
                ZigbeeGatewayStatusEntity.builder().account(owner).gatewayId(gatewayId).lastSeen(heartbeat).build(),
                ZigbeeGatewayStatusEntity.builder().account(owner).gatewayId(staleId).lastSeen(Instant.now().minusSeconds(3600)).build()));

        var clients = service.findClients();
        assertEquals(2, clients.size());
        assertTrue(clients.getFirst().online());
        assertEquals("ZIGBEE_GATEWAY", clients.getFirst().type());
        assertNull(clients.getFirst().calledAt());
        assertEquals(heartbeat, clients.getFirst().lastCommunication());
        assertFalse(clients.getLast().online());
        assertNotNull(clients.getLast().calledAt());
        assertEquals(7L, clients.getLast().accountId());
    }

    @Test
    void unresolvedAndInvalidCallersHaveUnknownOwnerAndStateAndRemainDistinctByType() {
        calls.recordControlDeviceCall("invalid-id", "192.0.2.1", "/control/{deviceUuid}");
        calls.recordZigbeeGatewaySync("invalid-id", "192.0.2.1");
        calls.recordControlDeviceCall(null, null, "/control/{deviceUuid}");

        var clients = service.findClients();
        assertEquals(3, clients.size());
        assertTrue(clients.stream().allMatch(client -> client.accountId() == null && client.online() == null));
        assertTrue(clients.stream().allMatch(client -> client.lastCommunication() == null));
        assertTrue(clients.stream().allMatch(client -> client.calledAt() != null));
        verify(devices).findConnectionMapDevices(List.of());
    }

    private DeviceEntity device(DeviceType type, boolean api, boolean mqtt) {
        return DeviceEntity.builder().uuid(UUID.randomUUID()).account(owner).deviceName("Test " + type)
                .deviceType(type).apiOnline(api).mqttOnline(mqtt).lastCommunication(Instant.now().minusSeconds(600)).build();
    }
}
