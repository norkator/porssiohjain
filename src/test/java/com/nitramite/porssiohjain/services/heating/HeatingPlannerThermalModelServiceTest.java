/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HeatingPlannerThermalModelServiceTest {

    @Test
    void estimatesFasterCoolingForLargerIndoorOutdoorDifference() {
        HeatingPlannerThermalModelService service = new HeatingPlannerThermalModelService(mock(), mock(), mock());
        Instant start = Instant.parse("2026-01-15T00:00:00Z");
        List<ZigbeeDeviceMeasurementEntity> temperatures = new ArrayList<>();
        List<SiteWeatherEntity> weather = new ArrayList<>();
        BigDecimal room = new BigDecimal("21.00");
        temperatures.add(temperature(start, room));
        for (int i = 1; i <= 40; i++) {
            BigDecimal outside = i <= 20 ? new BigDecimal("-5.0") : new BigDecimal("-10.0");
            BigDecimal cooling = room.subtract(outside).multiply(new BigDecimal("0.012"));
            room = room.subtract(cooling);
            Instant time = start.plus(Duration.ofHours(i));
            temperatures.add(temperature(time, room));
            weather.add(weather(time.minus(Duration.ofMinutes(30)), outside));
        }

        HeatingPlannerThermalModelService.Estimate estimate = service.estimate(temperatures, weather);

        assertThat(estimate.sampleCount()).isGreaterThanOrEqualTo(35);
        assertThat(estimate.outdoorLossRate()).isBetween(new BigDecimal("0.010"), new BigDecimal("0.014"));
        assertThat(estimate.confidence()).isPositive();
    }

    private ZigbeeDeviceMeasurementEntity temperature(Instant time, BigDecimal value) {
        return ZigbeeDeviceMeasurementEntity.builder().measuredAt(time).value(value).build();
    }

    private SiteWeatherEntity weather(Instant time, BigDecimal temperature) {
        return SiteWeatherEntity.builder().forecastTime(time).temperature(temperature)
                .windSpeedMs(BigDecimal.ZERO).build();
    }
}
