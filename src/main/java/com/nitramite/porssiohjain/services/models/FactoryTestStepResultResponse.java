package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.FactoryTestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryTestStepResultResponse {
    private Long id;
    private String stepKey;
    private FactoryTestStatus status;
    private String expectedValue;
    private String actualValue;
    private String details;
    private Instant createdAt;
}
