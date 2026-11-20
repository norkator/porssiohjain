/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeGatewayStatusRepository;
import com.nitramite.porssiohjain.services.AdminClientCallLogService.ClientType;
import com.nitramite.porssiohjain.services.AdminClientCallLogService.DeviceCallLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Combines observed HTTP callers with persisted connectivity, without polling device providers. */
@Service
@RequiredArgsConstructor
public class AdminDeviceConnectionMapService {
    private final AdminClientCallLogService callLogService;
    private final DeviceRepository deviceRepository;
    private final ZigbeeGatewayStatusRepository gatewayRepository;

    @Transactional(readOnly = true)
    public List<MapClient> findClients() {
        Map<ClientKey, DeviceCallLog> calls = new LinkedHashMap<>();
        for (DeviceCallLog call : callLogService.findLatestClients()) {
            calls.putIfAbsent(new ClientKey(call.clientType(), normalizeId(call.clientId())), call);
        }
        Map<ClientKey, MapClient> clients = new LinkedHashMap<>();
        for (DeviceEntity device : deviceRepository.findConnectionMapDevices(clientIds(calls, ClientType.CONTROL_DEVICE))) {
            String id = device.getUuid().toString();
            ClientKey key = new ClientKey(ClientType.CONTROL_DEVICE, id);
            String connection = device.isApiOnline() && device.isMqttOnline() ? "API + MQTT"
                    : device.isApiOnline() ? "API" : device.isMqttOnline() ? "MQTT" : "";
            clients.put(key, client(deviceType(device.getDeviceType()), id, device.getDeviceName(), device.getAccount(),
                    device.isApiOnline() || device.isMqttOnline(), connection, device.getLastCommunication(), calls.get(key)));
        }
        Instant cutoff = Instant.now().minus(ZigbeeGatewayConnectivityService.OFFLINE_AFTER);
        for (var gateway : gatewayRepository.findConnectionMapGateways(clientIds(calls, ClientType.ZIGBEE_GATEWAY), cutoff)) {
            String id = gateway.getGatewayId().toString();
            ClientKey key = new ClientKey(ClientType.ZIGBEE_GATEWAY, id);
            boolean online = !gateway.isOffline() && gateway.getLastSeen() != null && !gateway.getLastSeen().isBefore(cutoff);
            clients.put(key, client("ZIGBEE_GATEWAY", id, null, gateway.getAccount(), online, "API",
                    gateway.getLastSeen(), calls.get(key)));
        }
        // Invalid/unregistered callers remain visible, without guessing their owner or online state.
        calls.forEach((key, call) -> clients.computeIfAbsent(key, ignored -> client(call.clientType().name(),
                call.clientId(), null, null, null, "", null, call)));
        return List.copyOf(clients.values());
    }

    private List<UUID> clientIds(Map<ClientKey, DeviceCallLog> calls, ClientType type) {
        return calls.keySet().stream().filter(key -> key.type() == type)
                .map(key -> uuid(key.id())).filter(java.util.Objects::nonNull).distinct().toList();
    }

    private String normalizeId(String id) {
        UUID uuid = uuid(id);
        return uuid == null ? id : uuid.toString();
    }

    private UUID uuid(String id) {
        if (id == null) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String deviceType(DeviceType type) {
        return type == null || type == DeviceType.STANDARD ? "CONTROL_DEVICE" : type.name();
    }

    private MapClient client(String type, String id, String name, AccountEntity account, Boolean online,
                             String connection, Instant lastCommunication, DeviceCallLog call) {
        return new MapClient(type, id, name, account == null ? null : account.getId(),
                account == null ? null : account.getEmail(), online, connection, lastCommunication,
                call == null ? null : call.calledAt(), call == null ? null : call.clientIp(),
                call == null ? null : call.endpoint());
    }

    private record ClientKey(ClientType type, String id) { }

    /** Explicit admin UI contract; never includes account secrets or integration credentials. */
    public record MapClient(String type, String id, String name, Long accountId, String accountEmail,
                            Boolean online, String connection, Instant lastCommunication,
                            Instant calledAt, String ip, String endpoint) { }
}
