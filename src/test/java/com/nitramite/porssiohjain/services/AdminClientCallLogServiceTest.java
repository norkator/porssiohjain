package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.services.AdminClientCallLogService.ClientType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminClientCallLogServiceTest {
    private final AdminClientCallLogService service = new AdminClientCallLogService();

    @Test
    void mapUsesClientIdentityAndTypeRatherThanIpAndKeepsLatestDetails() {
        service.recordControlDeviceCall("shared-id", "192.0.2.1", "/control/{deviceUuid}");
        service.recordZigbeeGatewaySync("shared-id", "192.0.2.1");
        service.recordControlDeviceCall("another-device", "192.0.2.1", "/control/{deviceUuid}");
        service.recordControlDeviceCall("shared-id", "192.0.2.2", "/control/{deviceUuid}/timetable");

        var clients = service.findLatestClients();
        assertEquals(3, clients.size());
        var latest = clients.getFirst();
        assertEquals(ClientType.CONTROL_DEVICE, latest.clientType());
        assertEquals("shared-id", latest.clientId());
        assertEquals("192.0.2.2", latest.clientIp());
        assertEquals("/control/{deviceUuid}/timetable", latest.endpoint());
        assertEquals(service.findLatest().getFirst().calledAt(), latest.calledAt());
        assertTrue(clients.stream().anyMatch(client -> client.clientType() == ClientType.ZIGBEE_GATEWAY));
    }

    @Test
    void frequentCallerDoesNotDisplaceOtherClientsFromMapOrExpandEventLog() {
        service.recordZigbeeGatewaySync("quiet-gateway", "192.0.2.1");
        for (int call = 0; call < 40; call++) {
            service.recordControlDeviceCall("frequent-device", "192.0.2.2", "/control/{deviceUuid}");
        }

        assertEquals(30, service.findLatest().size());
        assertTrue(service.findLatest().stream().allMatch(call -> call.clientId().equals("frequent-device")));
        assertEquals(2, service.findLatestClients().size());
        assertEquals("quiet-gateway", service.findLatestClients().getLast().clientId());
    }

    @Test
    void boundedMapEvictsLeastRecentlyCallingClient() {
        for (int client = 0; client < 100; client++) {
            service.recordControlDeviceCall("device-" + client, "192.0.2.1", "/control/{deviceUuid}");
        }
        service.recordControlDeviceCall("device-0", "192.0.2.2", "/control/{deviceUuid}");
        service.recordZigbeeGatewaySync("new-gateway", "192.0.2.3");

        var clients = service.findLatestClients();
        assertEquals(100, clients.size());
        assertEquals("new-gateway", clients.getFirst().clientId());
        assertEquals("device-0", clients.get(1).clientId());
        assertFalse(clients.stream().anyMatch(call -> call.clientId().equals("device-1")));
        assertEquals(30, service.findLatest().size());
    }
}
