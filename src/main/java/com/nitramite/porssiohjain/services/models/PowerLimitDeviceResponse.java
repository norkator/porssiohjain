package com.nitramite.porssiohjain.services.models;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PowerLimitDeviceResponse {
    private Long id;
    private Long deviceId;
    private Integer deviceChannel;
    private DeviceResponse device;
    private Long powerLimitId;
}
