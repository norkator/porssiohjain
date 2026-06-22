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
import com.nitramite.porssiohjain.services.DeviceOfflineNotificationService;
import com.nitramite.porssiohjain.services.FactoryProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class MqttListener {

    private final DeviceRepository deviceRepository;
    private final FactoryProvisioningService factoryProvisioningService;
    private final DeviceOfflineNotificationService deviceOfflineNotificationService;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        Object topicHeader = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        if (topicHeader == null) {
            log.warn("Ignoring MQTT message without received topic header. headers={}", message.getHeaders());
            return;
        }
        String topic = topicHeader.toString();
        Object rawPayload = message.getPayload();
        String payload = rawPayload instanceof byte[]
                ? new String((byte[]) rawPayload, StandardCharsets.UTF_8)
                : rawPayload.toString();
        boolean retained = Boolean.TRUE.equals(message.getHeaders().get(MqttHeaders.RECEIVED_RETAINED));
        // log.info("MQTT inbound message topic='{}', retained={}, payload='{}'", topic, retained, abbreviatePayload(payload));
        Optional<UUID> onlineDeviceUuid = extractOnlineDeviceUuid(topic);
        if (onlineDeviceUuid.isPresent()) {
            Boolean online = parseOnlineStatusPayload(payload);
            if (online == null) {
                log.warn("Ignoring MQTT online status topic '{}' with unsupported payload '{}'", topic, payload);
                return;
            }
            UUID deviceUuid = onlineDeviceUuid.get();
            deviceRepository.findWithAccountByUuid(deviceUuid)
                    .ifPresentOrElse(device -> {
                        boolean wasApiOnline = device.isApiOnline();
                        boolean wasMqttOnline = device.isMqttOnline();
                        Instant now = Instant.now();
                        device.setMqttOnline(online);
                        device.setLastCommunication(now);
                        deviceRepository.save(device);
                        log.info(
                                "MQTT availability update deviceUuid={}, topic='{}', retained={}, mqttOnline {} -> {}, apiOnline={}",
                                device.getUuid(),
                                topic,
                                retained,
                                wasMqttOnline,
                                online,
                                wasApiOnline
                        );
                        if (retained) {
                            return;
                        }
                        if (online) {
                            deviceOfflineNotificationService.sendIfDeviceCameOnline(
                                    device,
                                    wasApiOnline,
                                    wasMqttOnline,
                                    "MQTT",
                                    now
                            );
                        } else {
                            deviceOfflineNotificationService.sendIfDeviceWentOffline(
                                    device,
                                    wasApiOnline,
                                    wasMqttOnline,
                                    "MQTT",
                                    now
                            );
                        }
                    }, () -> {
                        log.warn("Ignoring MQTT availability topic '{}' because device UUID {} was not found", topic, deviceUuid);
                    });
            return;
        }
        if (topic.startsWith("factory/bootstrap/")) {
            factoryProvisioningService.registerBootstrapMessage(topic, payload);
        }
    }

    private Optional<UUID> extractOnlineDeviceUuid(String topic) {
        String deviceId = null;
        if (topic.endsWith("/online")) {
            deviceId = topic.substring(0, topic.indexOf("/"));
        } else if (topic.endsWith(".online")) {
            deviceId = topic.substring(0, topic.length() - ".online".length());
        } else if (topic.endsWith(".connected")) {
            deviceId = topic.substring(0, topic.length() - ".connected".length());
        } else if (topic.endsWith("/connected")) {
            deviceId = topic.substring(0, topic.indexOf("/"));
        }

        if (deviceId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(deviceId));
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring MQTT online status topic '{}' because '{}' is not a valid device UUID", topic, deviceId);
            return Optional.empty();
        }
    }

    private Boolean parseOnlineStatusPayload(String payload) {
        String normalized = payload == null ? "" : payload.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "online", "1" -> true;
            case "false", "offline", "0" -> false;
            default -> null;
        };
    }

}
