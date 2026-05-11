package com.nitramite.porssiohjain.services.models;

import lombok.Data;

@Data
public class ClaimProvisionedDeviceRequest {
    private String claimCode;
    private String deviceName;
    private String timezone;
}
