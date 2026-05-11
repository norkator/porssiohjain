package com.nitramite.porssiohjain.services.models;

import lombok.Data;

@Data
public class ClaimFactoryDeviceRequest {
    private Long accountId;
    private String deviceName;
    private String timezone;
}
