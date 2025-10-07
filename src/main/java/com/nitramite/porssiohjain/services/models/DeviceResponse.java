package com.nitramite.porssiohjain.services.models;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DeviceResponse {
    private Long id;
    private UUID uuid;
    private String deviceName;
    private Instant lastCommunication;
    private Instant createdAt;
    private Instant updatedAt;
}