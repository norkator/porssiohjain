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

import com.nitramite.porssiohjain.entity.enums.MqttDeviceProfile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttServiceTest {

    @Test
    void publishesGenericRelayCommandsUsingCommandTopic() {
        MessageChannel messageChannel = mock(MessageChannel.class);
        when(messageChannel.send(any(Message.class))).thenReturn(true);
        MqttService mqttService = new MqttService(messageChannel);

        mqttService.switchControl("device-123", 0, true);

        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageChannel).send(messageCaptor.capture());
        assertEquals("device-123/command/switch:0", messageCaptor.getValue().getHeaders().get("mqtt_topic"));
        assertEquals("on", messageCaptor.getValue().getPayload());
    }

    @Test
    void publishesOpenBekenRelayCommandsUsingPowerTopics() {
        MessageChannel messageChannel = mock(MessageChannel.class);
        when(messageChannel.send(any(Message.class))).thenReturn(true);
        MqttService mqttService = new MqttService(messageChannel);

        mqttService.switchControl("device-123", MqttDeviceProfile.OPENBEKEN_RELAY, 1, false);

        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageChannel).send(messageCaptor.capture());
        assertEquals("cmnd/device-123/Power2", messageCaptor.getValue().getHeaders().get("mqtt_topic"));
        assertEquals("0", messageCaptor.getValue().getPayload());
    }
}
