package com.nitramite.porssiohjain.services.models;

import lombok.Data;

@Data
public class CreateOtaDeploymentRequest {
    private Long otaReleaseId;
    private String commandTemplate;
}
