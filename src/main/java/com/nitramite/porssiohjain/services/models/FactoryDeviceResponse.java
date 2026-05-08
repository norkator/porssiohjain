package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.FactoryDeviceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryDeviceResponse {
    private Long id;
    private String serialNumber;
    private String hardwareMac;
    private String chipId;
    private DevicePlatform platform;
    private String productModel;
    private String firmwareVersion;
    private String mqttTopicRoot;
    private String mqttUsername;
    private String mqttPassword;
    private FactoryDeviceStatus status;
    private Instant lastSeenAt;
    private String lastBootstrapPayload;
    private String metadataJson;
    private Long claimedDeviceId;
    private Instant createdAt;
    private Instant updatedAt;
    private List<FactoryTestRunResponse> testRuns;
    private List<OtaDeploymentResponse> otaDeployments;
}
