package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtaReleaseResponse {
    private Long id;
    private DevicePlatform platform;
    private String productModel;
    private String version;
    private String binaryUrl;
    private String checksumSha256;
    private boolean active;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
