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

import com.nitramite.porssiohjain.services.models.SystemLogResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Service
public class SystemLogService {

    private static final int MAX_LOG_ENTRIES = 20;
    private final LinkedList<SystemLogResponse> logs = new LinkedList<>();
    private final LinkedList<SystemLogResponse> mqttLogs = new LinkedList<>();

    public synchronized void log(
            String message
    ) {
        append(logs, message);
    }

    public synchronized void logMqtt(
            String message
    ) {
        append(mqttLogs, message);
    }

    public synchronized List<SystemLogResponse> findLatest() {
        return Collections.unmodifiableList(new LinkedList<>(logs));
    }

    public synchronized List<SystemLogResponse> findLatestMqtt() {
        return Collections.unmodifiableList(new LinkedList<>(mqttLogs));
    }

    private void append(
            LinkedList<SystemLogResponse> target,
            String message
    ) {
        SystemLogResponse entry = SystemLogResponse.builder()
                .createdAt(Instant.now())
                .message(message)
                .build();

        target.addFirst(entry);
        if (target.size() > MAX_LOG_ENTRIES) {
            target.removeLast();
        }
    }

}
