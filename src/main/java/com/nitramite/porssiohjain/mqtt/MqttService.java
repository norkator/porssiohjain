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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
@Service
public class MqttService {

    private final MessageChannel mqttOutboundChannel;

    public MqttService(@Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel) {
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    public void switchControl(String deviceId, int channel, boolean on) {
        String topic = deviceId + "/command/switch:" + channel;
        String payload = on ? "on" : "off";
        mqttOutboundChannel.send(new GenericMessage<>(payload,
                Map.of("mqtt_topic", topic)));
    }

    public void setThermostatTemperature(String deviceId, int channel, BigDecimal targetTemperature) {
        String topic = deviceId + "/command/thermostat:" + channel;
        String payload = "{\"targetTemperature\":" + targetTemperature.toPlainString() + "}";
        mqttOutboundChannel.send(new GenericMessage<>(payload,
                Map.of("mqtt_topic", topic)));
    }

}
