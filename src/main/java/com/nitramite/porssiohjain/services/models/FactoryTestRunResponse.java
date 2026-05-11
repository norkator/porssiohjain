package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.FactoryTestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryTestRunResponse {
    private Long id;
    private Long factoryDeviceId;
    private Long operatorAccountId;
    private String stationName;
    private FactoryTestStatus status;
    private String notes;
    private Instant startedAt;
    private Instant finishedAt;
    private List<FactoryTestStepResultResponse> steps;
}
