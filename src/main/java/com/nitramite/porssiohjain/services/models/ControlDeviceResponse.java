package com.nitramite.porssiohjain.services.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ControlDeviceResponse {
    private Long id;
    private Long controlId;
    private Long deviceId;
    private Integer deviceChannel;
    private DeviceResponse device;
}