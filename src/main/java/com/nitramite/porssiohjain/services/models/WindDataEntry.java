package com.nitramite.porssiohjain.services.models;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class WindDataEntry {

    private int datasetId;
    private Instant startTime;
    private Instant endTime;
    private BigDecimal value;

}