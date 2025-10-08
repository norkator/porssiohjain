package com.nitramite.porssiohjain.services.models;

import lombok.Data;

@Data
public class CreateDeviceRequest {
    private String deviceName;
    private String timezone;
}