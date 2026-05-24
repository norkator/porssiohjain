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
import com.nitramite.porssiohjain.services.FactoryProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.springframework.messaging.Message;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class MqttListener {

    private final DeviceRepository deviceRepository;
    private final FactoryProvisioningService factoryProvisioningService;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        String topic = message.getHeaders().get("mqtt_receivedTopic").toString();
        Object rawPayload = message.getPayload();
        String payload = rawPayload instanceof byte[]
                ? new String((byte[]) rawPayload)
                : rawPayload.toString();
        // log.info("Received: {} -> {}", topic, payload);
        Optional<UUID> deviceUuid = extractDeviceUuid(topic);
        if (deviceUuid.isPresent()) {
            updateDevicePresence(deviceUuid.get(), parseOnlineState(topic, payload));
            return;
        }
        if (topic.startsWith("factory/bootstrap/")) {
            factoryProvisioningService.registerBootstrapMessage(topic, payload);
        }
    }

    private Optional<UUID> extractDeviceUuid(String topic) {
        if (topic.endsWith("/online")) {
            return parseUuid(topic.substring(0, topic.indexOf("/")));
        }
        if (topic.endsWith(".online")) {
            return parseUuid(topic.substring(0, topic.length() - ".online".length()));
        }
        if (topic.endsWith(".connected")) {
            return parseUuid(topic.substring(0, topic.length() - ".connected".length()));
        }
        int slashIndex = topic.indexOf('/');
        if (slashIndex > 0) {
            return parseUuid(topic.substring(0, slashIndex));
        }
        int dotIndex = topic.indexOf('.');
        if (dotIndex > 0) {
            return parseUuid(topic.substring(0, dotIndex));
        }
        return Optional.empty();
    }

    private void updateDevicePresence(UUID deviceUuid, boolean online) {
        deviceRepository.findByUuid(deviceUuid)
                .ifPresent(device -> {
                    device.setMqttOnline(online);
                    device.setLastCommunication(Instant.now());
                    deviceRepository.save(device);
                });
    }

    private boolean parseOnlineState(String topic, String payload) {
        if (topic.endsWith("/online") || topic.endsWith(".online") || topic.endsWith(".connected")) {
            return parseOnlinePayload(payload);
        }
        return true;
    }

    private boolean parseOnlinePayload(String payload) {
        String normalized = payload == null ? "" : payload.trim();
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "online".equalsIgnoreCase(normalized)
                || "connected".equalsIgnoreCase(normalized);
    }

    private Optional<UUID> parseUuid(String candidate) {
        try {
            return Optional.of(UUID.fromString(candidate));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

}
