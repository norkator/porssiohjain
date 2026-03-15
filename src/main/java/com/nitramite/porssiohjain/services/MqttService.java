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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MqttService {

    private final DeviceRepository deviceRepository;
    private final MqttPahoClientFactory mqttClientFactory;

    private MqttPahoMessageHandler publisher;

    @PostConstruct
    public void init() {
        System.out.println("[MqttService] Initializing MQTT subscriber and publisher...");

        publisher = new MqttPahoMessageHandler("spring-publisher", mqttClientFactory);
        publisher.setAsync(true);

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        "spring-subscriber",
                        mqttClientFactory,
                        "devices/+/status",
                        "devices/+/telemetry"
                );
        DirectChannel channel = new DirectChannel();
        adapter.setOutputChannel(channel);
        channel.subscribe(this::handleMqttMessage);

        System.out.println("[MqttService] MQTT subscriber initialized for topics: devices/+/status, devices/+/telemetry");
    }

    private void handleMqttMessage(Message<?> msg) {
        String topic = (String) msg.getHeaders().get("mqtt_receivedTopic");
        String payload = msg.getPayload().toString();
        System.out.println("[MqttService] Received message: topic=" + topic + ", payload=" + payload);

        String[] parts = topic.split("/");
        if (parts.length < 2) return;

        String deviceUuidStr = parts[1];

        deviceRepository.findByUuid(UUID.fromString(deviceUuidStr)).ifPresent(device -> {
            device.setLastCommunication(Instant.now());
            if (topic.endsWith("status")) {
                device.setOnline("online".equalsIgnoreCase(payload));
                System.out.println("[MqttService] Updated device " + device.getUuid() + " online=" + device.isOnline());
            } else if (topic.endsWith("telemetry")) {
                device.setLastTelemetry(payload);
                System.out.println("[MqttService] Updated device " + device.getUuid() + " lastTelemetry=" + payload);
            }
            deviceRepository.save(device);
        });
    }

    public void sendCommand(DeviceEntity device, String payload) {
        String topic = "devices/" + device.getUuid() + "/command";
        System.out.println("[MqttService] Sending command to device " + device.getUuid() + " topic=" + topic + " payload=" + payload);
        publisher.handleMessage(
                MessageBuilder.withPayload(payload)
                        .setHeader(MqttHeaders.TOPIC, topic)
                        .build()
        );
    }
}