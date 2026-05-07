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

package com.nitramite.porssiohjain.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ThermostatCurveService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BigDecimal evaluate(String curveJson, BigDecimal price) {
        List<CurvePoint> points = parseCurve(curveJson);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Thermostat curve must contain at least one point");
        }

        if (points.size() == 1) {
            return points.getFirst().temperature().setScale(2, RoundingMode.HALF_UP);
        }

        if (price.compareTo(points.getFirst().price()) <= 0) {
            return points.getFirst().temperature().setScale(2, RoundingMode.HALF_UP);
        }
        if (price.compareTo(points.getLast().price()) >= 0) {
            return points.getLast().temperature().setScale(2, RoundingMode.HALF_UP);
        }

        for (int i = 1; i < points.size(); i++) {
            CurvePoint left = points.get(i - 1);
            CurvePoint right = points.get(i);
            if (price.compareTo(right.price()) <= 0) {
                BigDecimal span = right.price().subtract(left.price());
                if (span.compareTo(BigDecimal.ZERO) == 0) {
                    return right.temperature().setScale(2, RoundingMode.HALF_UP);
                }
                BigDecimal position = price.subtract(left.price())
                        .divide(span, 8, RoundingMode.HALF_UP);
                BigDecimal temperatureDelta = right.temperature().subtract(left.temperature());
                return left.temperature()
                        .add(temperatureDelta.multiply(position))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        return points.getLast().temperature().setScale(2, RoundingMode.HALF_UP);
    }

    public String normalizeCurveJson(String curveJson) {
        List<CurvePoint> points = parseCurve(curveJson);
        try {
            return objectMapper.writeValueAsString(points);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize thermostat curve", e);
        }
    }

    public List<CurvePoint> parseCurve(String curveJson) {
        try {
            List<CurvePoint> points = objectMapper.readValue(curveJson, new TypeReference<>() {});
            if (points == null || points.isEmpty()) {
                throw new IllegalArgumentException("Thermostat curve must contain at least one point");
            }
            List<CurvePoint> sorted = points.stream()
                    .peek(point -> {
                        if (point.price() == null || point.temperature() == null) {
                            throw new IllegalArgumentException("Each thermostat curve point must include price and temperature");
                        }
                    })
                    .sorted(Comparator.comparing(CurvePoint::price))
                    .toList();

            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).price().compareTo(sorted.get(i - 1).price()) == 0) {
                    throw new IllegalArgumentException("Thermostat curve prices must be unique");
                }
            }
            return sorted;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid thermostat curve JSON", e);
        }
    }

    public record CurvePoint(BigDecimal price, BigDecimal temperature) {
    }
}
