package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.development.LocalDevelopmentConfiguration;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.ZigbeeGatewayStatusEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeGatewayStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.config.location=classpath:/application-local.properties")
@ActiveProfiles("local")
@Transactional
class AdminDeviceConnectionMapIntegrationTest {
    @Autowired AccountRepository accounts;
    @Autowired DeviceRepository devices;
    @Autowired ZigbeeGatewayStatusRepository gateways;

    @Test
    void queriesIncludeOnlineDevicesAndRecordedOfflineCallersWithoutLoadingUnrelatedOfflineDevices() {
        var account = accounts.findByUuid(LocalDevelopmentConfiguration.ADMIN_UUID).orElseThrow();
        var pump = devices.saveAndFlush(DeviceEntity.builder().account(account).deviceType(DeviceType.HEAT_PUMP)
                .deviceName("Heat pump").timezone("Europe/Helsinki").apiOnline(true).build());
        var mqtt = devices.saveAndFlush(DeviceEntity.builder().account(account).deviceType(DeviceType.TEMPERATURE_SENSOR)
                .deviceName("Sensor").timezone("Europe/Helsinki").mqttOnline(true).build());
        var offline = devices.saveAndFlush(DeviceEntity.builder().account(account)
                .deviceName("Recorded relay").timezone("Europe/Helsinki").build());
        devices.saveAndFlush(DeviceEntity.builder().account(account)
                .deviceName("Other offline relay").timezone("Europe/Helsinki").build());
        var freshGateway = gateways.saveAndFlush(ZigbeeGatewayStatusEntity.builder().account(account)
                .gatewayId(UUID.randomUUID()).lastSeen(Instant.now().minusSeconds(300)).build());
        var staleGateway = gateways.saveAndFlush(ZigbeeGatewayStatusEntity.builder().account(account)
                .gatewayId(UUID.randomUUID()).lastSeen(Instant.now().minusSeconds(3600)).build());
        var offlineGateway = gateways.saveAndFlush(ZigbeeGatewayStatusEntity.builder().account(account)
                .gatewayId(UUID.randomUUID()).lastSeen(Instant.now()).offline(true).build());
        var calls = new AdminClientCallLogService();
        var service = new AdminDeviceConnectionMapService(calls, devices, gateways);

        var online = service.findClients();
        assertEquals(3, online.size());
        assertEquals(List.of(pump.getUuid().toString(), mqtt.getUuid().toString(), freshGateway.getGatewayId().toString()).stream().sorted().toList(),
                online.stream().map(AdminDeviceConnectionMapService.MapClient::id).sorted().toList());
        assertTrue(online.stream().allMatch(client -> client.accountId().equals(account.getId()) && client.online()));

        calls.recordControlDeviceCall(offline.getUuid().toString(), "192.0.2.1", "/control/{deviceUuid}");
        calls.recordZigbeeGatewaySync(staleGateway.getGatewayId().toString(), "192.0.2.2");
        calls.recordZigbeeGatewaySync(offlineGateway.getGatewayId().toString(), "192.0.2.3");
        var clients = service.findClients();
        assertEquals(6, clients.size());
        assertEquals(3, clients.stream().filter(client -> Boolean.FALSE.equals(client.online())).count());
        assertEquals(3, clients.stream().filter(client -> client.calledAt() != null).count());
        assertTrue(clients.stream().allMatch(client -> client.accountId().equals(account.getId())));
    }
}
