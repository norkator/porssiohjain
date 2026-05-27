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

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.DeviceOfflineNotificationService;
import com.nitramite.porssiohjain.services.FactoryProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MqttListenerTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private FactoryProvisioningService factoryProvisioningService;

    @Mock
    private DeviceOfflineNotificationService deviceOfflineNotificationService;

    private MqttListener listener;
    private DeviceEntity device;

    @BeforeEach
    void setUp() {
        listener = new MqttListener(deviceRepository, factoryProvisioningService, deviceOfflineNotificationService);
        device = DeviceEntity.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .mqttUsername("device-user")
                .mqttPassword("secret")
                .build();
    }

    @Test
    void marksDeviceOnlineForSlashTopic() {
        when(deviceRepository.findWithAccountByUuid(device.getUuid())).thenReturn(Optional.of(device));

        listener.handleMessage(MessageBuilder.withPayload("true")
                .setHeader("mqtt_receivedTopic", device.getUuid() + "/online")
                .build());

        ArgumentCaptor<DeviceEntity> savedDevice = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(deviceRepository).save(savedDevice.capture());
        assertTrue(savedDevice.getValue().isMqttOnline());
        assertNotNull(savedDevice.getValue().getLastCommunication());
        verify(deviceOfflineNotificationService, never()).sendIfDeviceWentOffline(any(), anyBoolean(), anyBoolean(), any(), any());
        verify(deviceOfflineNotificationService).sendIfDeviceCameOnline(eq(device), eq(false), eq(false), eq("MQTT"), any());
    }

    @Test
    void marksDeviceOnlineForDotTopic() {
        when(deviceRepository.findWithAccountByUuid(device.getUuid())).thenReturn(Optional.of(device));

        listener.handleMessage(MessageBuilder.withPayload("true")
                .setHeader("mqtt_receivedTopic", device.getUuid() + ".online")
                .build());

        ArgumentCaptor<DeviceEntity> savedDevice = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(deviceRepository).save(savedDevice.capture());
        assertTrue(savedDevice.getValue().isMqttOnline());
        assertNotNull(savedDevice.getValue().getLastCommunication());
    }

    @Test
    void sendsOfflineNotificationForMqttOfflineTransition() {
        device.setMqttOnline(true);
        when(deviceRepository.findWithAccountByUuid(device.getUuid())).thenReturn(Optional.of(device));

        listener.handleMessage(MessageBuilder.withPayload("false")
                .setHeader("mqtt_receivedTopic", device.getUuid() + "/online")
                .build());

        ArgumentCaptor<DeviceEntity> savedDevice = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(deviceRepository).save(savedDevice.capture());
        assertFalse(savedDevice.getValue().isMqttOnline());
        verify(deviceOfflineNotificationService).sendIfDeviceWentOffline(
                eq(device),
                eq(false),
                eq(true),
                eq("MQTT"),
                any()
        );
    }

    @Test
    void registersFactoryBootstrapMessages() {
        listener.handleMessage(MessageBuilder.withPayload("{\"ok\":true}")
                .setHeader("mqtt_receivedTopic", "factory/bootstrap/SER-001/state")
                .build());

        verify(factoryProvisioningService).registerBootstrapMessage(
                "factory/bootstrap/SER-001/state",
                "{\"ok\":true}"
        );
    }
}
