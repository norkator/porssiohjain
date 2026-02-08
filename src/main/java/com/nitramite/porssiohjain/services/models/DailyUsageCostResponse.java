package com.nitramite.porssiohjain.services.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyUsageCostResponse {
    LocalDate date;
    BigDecimal totalUsageKwh;
    BigDecimal totalCostEur;
}
