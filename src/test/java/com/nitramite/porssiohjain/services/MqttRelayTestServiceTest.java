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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.services.models.DeviceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class MqttRelayTestServiceTest {

    private ControlService controlService;
    private MqttRelayTestService mqttRelayTestService;

    @BeforeEach
    void setUp() {
        controlService = mock(ControlService.class);
        mqttRelayTestService = new MqttRelayTestService(controlService);
    }

    @Test
    void startAndStopTestSendsOnThenOff() {
        DeviceResponse device = createDevice();

        mqttRelayTestService.startTest(device, 1, 5);
        assertTrue(mqttRelayTestService.isRunning(10L, 20L));

        mqttRelayTestService.stopTest(10L, 20L);
        assertFalse(mqttRelayTestService.isRunning(10L, 20L));

        InOrder inOrder = inOrder(controlService);
        inOrder.verify(controlService).sendDebugMqttRelayCommand(10L, 20L, 1, true);
        inOrder.verify(controlService).sendDebugMqttRelayCommand(10L, 20L, 1, false);
    }

    @Test
    void rejectsUnsupportedInterval() {
        DeviceResponse device = createDevice();

        assertThrows(IllegalArgumentException.class, () -> mqttRelayTestService.startTest(device, 1, 7));
    }

    private DeviceResponse createDevice() {
        return DeviceResponse.builder()
                .accountId(10L)
                .id(20L)
                .uuid(UUID.randomUUID())
                .deviceName("Relay")
                .build();
    }
}
