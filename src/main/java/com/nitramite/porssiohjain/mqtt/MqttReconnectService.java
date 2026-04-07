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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
@Slf4j
@AllArgsConstructor
public class MqttReconnectService {

    private final MqttPahoMessageDrivenChannelAdapter adapter;

    public void reconnect() {
        try {
            adapter.stop();
            adapter.start();
            log.info("MQTT adapter reconnected.");
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

}