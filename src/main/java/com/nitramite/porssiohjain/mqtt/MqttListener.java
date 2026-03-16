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

package com.nitramite.porssiohjain.mqtt;

import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.springframework.messaging.Message;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class MqttListener {

    private final DeviceRepository deviceRepository;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        String topic = message.getHeaders().get("mqtt_receivedTopic").toString();
        Object rawPayload = message.getPayload();
        String payload = rawPayload instanceof byte[]
                ? new String((byte[]) rawPayload)
                : rawPayload.toString();
        // log.info("Received: {} -> {}", topic, payload);
        if (topic.endsWith("/online")) {
            String deviceId = topic.substring(0, topic.indexOf("/"));
            boolean online = Boolean.parseBoolean(payload);
            deviceRepository.findByUuid(UUID.fromString(deviceId))
                    .ifPresent(device -> {
                        device.setOnline(online);
                        device.setLastCommunication(Instant.now());
                        deviceRepository.save(device);
                    });
        }
    }

}