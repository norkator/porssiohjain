package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.ComparisonType;
import com.nitramite.porssiohjain.entity.ControlAction;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionSourceDeviceResponse {
    private Long id;
    private Long deviceId;
    private Integer deviceChannel;
    private DeviceResponse device;
    private Long sourceId;
    private BigDecimal triggerKw;
    private ComparisonType comparisonType;
    private ControlAction action;
    private boolean enabled;
}