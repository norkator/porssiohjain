package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.FactoryTestStatus;
import lombok.Data;

@Data
public class CreateFactoryTestStepRequest {
    private String stepKey;
    private FactoryTestStatus status;
    private String expectedValue;
    private String actualValue;
    private String details;
    private Boolean finalizeRun;
}
