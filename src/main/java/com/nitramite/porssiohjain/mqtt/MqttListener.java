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
                        device.setMqttOnline(online);
                        device.setLastCommunication(Instant.now());
                        deviceRepository.save(device);
                    });
        }
    }

}