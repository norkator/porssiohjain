package com.nitramite.porssiohjain.services.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {
    private Long id;
    private UUID uuid;
    private String deviceName;
    private String timezone;
    private Instant lastCommunication;
    private Instant createdAt;
    private Instant updatedAt;
}
