package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.OtaDeploymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtaDeploymentResponse {
    private Long id;
    private Long otaReleaseId;
    private Long factoryDeviceId;
    private Long deviceId;
    private Long requestedByAccountId;
    private OtaDeploymentStatus status;
    private String commandTopic;
    private String commandPayload;
    private String resultDetails;
    private Instant requestedAt;
    private Instant startedAt;
    private Instant finishedAt;
}
