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

package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitMqAuthControllerTest {

    @Mock
    private DeviceRepository deviceRepository;

    private RabbitMqAuthController controller;
    private DeviceEntity device;

    @BeforeEach
    void setUp() {
        controller = new RabbitMqAuthController(deviceRepository);
        device = DeviceEntity.builder()
                .uuid(UUID.randomUUID())
                .mqttUsername("device-user")
                .mqttPassword("secret")
                .build();
    }

    @Test
    void authenticatesDeviceUserWithMatchingPassword() {
        when(deviceRepository.findByMqttUsername("device-user")).thenReturn(Optional.of(device));

        assertEquals("allow", controller.authenticateUser("device-user", "secret", "client-1", "/").getBody());
    }

    @Test
    void deniesDeviceUserWithWrongPassword() {
        when(deviceRepository.findByMqttUsername("device-user")).thenReturn(Optional.of(device));

        assertEquals("deny", controller.authenticateUser("device-user", "wrong", "client-1", "/").getBody());
    }

    @Test
    void deniesDeviceExchangeWritePermission() {
        when(deviceRepository.findByMqttUsername("device-user")).thenReturn(Optional.of(device));

        assertEquals("deny", controller.authorizeResource("device-user", "/", "exchange", "amq.topic", "write").getBody());
    }

    @Test
    void allowsDeviceQueuePermissionsNeededForMqttSubscribe() {
        when(deviceRepository.findByMqttUsername("device-user")).thenReturn(Optional.of(device));

        assertEquals("allow", controller.authorizeResource("device-user", "/", "queue",
                "mqtt-subscription-client-1qos0", "configure").getBody());
        assertEquals("allow", controller.authorizeResource("device-user", "/", "queue",
                "mqtt-subscription-client-1qos0", "write").getBody());
        assertEquals("allow", controller.authorizeResource("device-user", "/", "queue",
                "mqtt-subscription-client-1qos0", "read").getBody());
    }

    @Test
    void deniesDeviceTopicWritePermission() {
        when(deviceRepository.findByMqttUsername("device-user")).thenReturn(Optional.of(device));

        assertEquals("deny", controller.authorizeTopic("device-user", "/", "topic", "amq.topic",
                "write", device.getUuid() + ".command.switch:1").getBody());
    }

    @Test
    void allowsDeviceToReadOwnCommandTopicOnly() {
        when(deviceRepository.findByMqttUsername("device-user")).thenReturn(Optional.of(device));

        assertEquals("allow", controller.authorizeTopic("device-user", "/", "topic", "amq.topic",
                "read", device.getUuid() + ".command.#").getBody());
        assertEquals("deny", controller.authorizeTopic("device-user", "/", "topic", "amq.topic",
                "read", UUID.randomUUID() + ".command.#").getBody());
    }

    @Test
    void leavesNonDeviceUsersOnExistingAllowBehavior() {
        when(deviceRepository.findByMqttUsername("spring-publisher")).thenReturn(Optional.empty());

        assertEquals("allow", controller.authorizeResource("spring-publisher", "/", "exchange", "amq.topic", "write").getBody());
        assertEquals("allow", controller.authorizeTopic("spring-publisher", "/", "topic", "amq.topic",
                "write", device.getUuid() + ".command.switch:1").getBody());
    }
}
