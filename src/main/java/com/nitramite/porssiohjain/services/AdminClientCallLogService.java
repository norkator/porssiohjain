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

package com.nitramite.porssiohjain.services;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminClientCallLogService {

    private static final int MAX_ROWS = 30;

    private final ArrayDeque<DeviceCallLog> logs = new ArrayDeque<>();

    public void recordControlDeviceCall(String deviceUuid, String clientIp, String endpoint) {
        recordCall(ClientType.CONTROL_DEVICE, deviceUuid, clientIp, endpoint);
    }

    public void recordZigbeeGatewaySync(String gatewayId, String clientIp) {
        recordCall(ClientType.ZIGBEE_GATEWAY, gatewayId, clientIp, "/zigbee-gateways/{gatewayId}/sync");
    }

    private synchronized void recordCall(ClientType clientType, String clientId, String clientIp, String endpoint) {
        logs.addFirst(new DeviceCallLog(Instant.now(), clientType, clientId, clientIp, endpoint));
        while (logs.size() > MAX_ROWS) {
            logs.removeLast();
        }
    }

    public synchronized List<DeviceCallLog> findLatest() {
        return List.copyOf(new ArrayList<>(logs));
    }

    public record DeviceCallLog(
            Instant calledAt,
            ClientType clientType,
            String clientId,
            String clientIp,
            String endpoint
    ) {
    }

    public enum ClientType {
        CONTROL_DEVICE("Control device"),
        ZIGBEE_GATEWAY("Zigbee gateway");

        private final String label;

        ClientType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
