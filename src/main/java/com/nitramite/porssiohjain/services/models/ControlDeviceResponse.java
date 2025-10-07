package com.nitramite.porssiohjain.services.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlDeviceResponse {
    private Long id;
    private Long controlId;
    private Long deviceId;
    private Integer deviceChannel;
    private DeviceResponse device;
}