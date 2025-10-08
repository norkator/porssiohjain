package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlTableResponse {
    private Long id;
    private Long controlId;
    private Long deviceId;
    private Integer deviceChannel;
    private BigDecimal priceSnt;
    private Status status;
    private Instant startTime;
    private Instant endTime;
    private DeviceResponse device;
}