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