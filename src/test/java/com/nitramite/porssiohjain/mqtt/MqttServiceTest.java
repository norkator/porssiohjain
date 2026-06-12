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

import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MqttServiceTest {

    private MessageChannel mqttOutboundChannel;
    private MqttService mqttService;

    @BeforeEach
    void setUp() {
        mqttOutboundChannel = mock(MessageChannel.class);
        mqttService = new MqttService(mqttOutboundChannel);
    }

    @Test
    void publishesGenericRelayCommand() {
        mqttService.switchControl("device-1", 2, true, DevicePlatform.GENERIC_MQTT);

        Message<?> message = captureOutboundMessage();
        assertEquals("device-1/command/switch:2", message.getHeaders().get("mqtt_topic"));
        assertEquals("on", message.getPayload());
    }

    @Test
    void publishesOpenBekenRelayCommand() {
        mqttService.switchControl("device-1", 1, true, DevicePlatform.OPENBEKEN);

        Message<?> message = captureOutboundMessage();
        assertEquals("device-1/1/set", message.getHeaders().get("mqtt_topic"));
        assertEquals("1", message.getPayload());
    }

    @Test
    void publishesOpenBekenRelayOffCommand() {
        mqttService.switchControl("device-1", 1, false, DevicePlatform.OPENBEKEN);

        Message<?> message = captureOutboundMessage();
        assertEquals("device-1/1/set", message.getHeaders().get("mqtt_topic"));
        assertEquals("0", message.getPayload());
    }

    private Message<?> captureOutboundMessage() {
        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mqttOutboundChannel).send(messageCaptor.capture());
        return messageCaptor.getValue();
    }

}
