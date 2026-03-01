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

    public synchronized void log(
            String message
    ) {
        SystemLogResponse entry = SystemLogResponse.builder()
                .createdAt(Instant.now())
                .message(message)
                .build();

        logs.addFirst(entry);
        if (logs.size() > MAX_LOG_ENTRIES) {
            logs.removeLast();
        }
    }

    public synchronized List<SystemLogResponse> findLatest() {
        return Collections.unmodifiableList(new LinkedList<>(logs));
    }

}