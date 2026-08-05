/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.services.ThermostatCurveService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThermostatCurveServiceTest {

    private final ThermostatCurveService thermostatCurveService = new ThermostatCurveService();

    @Test
    void selectsTemperatureFromClosestCurvePoint() {
        String curveJson = """
                [
                  {"price":0,"temperature":22.0},
                  {"price":10,"temperature":20.0},
                  {"price":20,"temperature":18.0}
                ]
                """;

        BigDecimal result = thermostatCurveService.evaluate(curveJson, BigDecimal.valueOf(6));

        assertEquals(new BigDecimal("20.0"), result);
    }

    @Test
    void usesFallbackOnlyWhenCurveHasNoPoints() {
        assertEquals(new BigDecimal("19.5"), thermostatCurveService.evaluateOrFallback(
                "[]", BigDecimal.valueOf(6), new BigDecimal("19.5")));
    }

    @Test
    void configuredCurveTakesPrecedenceOverFallback() {
        String curveJson = """
                [{"price":2,"temperature":22},{"price":5,"temperature":21}]
                """;

        assertEquals(new BigDecimal("21"), thermostatCurveService.evaluateOrFallback(
                curveJson, BigDecimal.valueOf(6), new BigDecimal("17")));
    }

    @Test
    void rejectsDuplicateCurvePrices() {
        String curveJson = """
                [
                  {"price":10,"temperature":22.0},
                  {"price":10,"temperature":20.0}
                ]
                """;

        assertThrows(IllegalArgumentException.class, () -> thermostatCurveService.normalizeCurveJson(curveJson));
    }
}
