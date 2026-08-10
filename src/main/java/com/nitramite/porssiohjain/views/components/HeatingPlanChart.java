/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@JsModule("./js/apexcharts.min.js")
public class HeatingPlanChart extends Div {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public HeatingPlanChart(List<HeatingPlanSimulationService.SimulationPoint> points, ZoneId zone) {
        setWidthFull();
        setHeight("390px");
        getStyle().set("position", "relative");

        List<String> labels = points.stream()
                .map(point -> TIME_FORMAT.format(point.time().atZone(zone)))
                .toList();
        List<Double> room = points.stream().map(point -> point.roomTemperature().doubleValue()).toList();
        List<Double> floor = points.stream().map(point -> point.floorTemperature().doubleValue()).toList();
        List<Double> setpoint = points.stream().map(point -> point.floorSetpoint().doubleValue()).toList();
        List<Double> outdoor = points.stream().map(point -> point.outdoorTemperature().doubleValue()).toList();
        List<Double> price = points.stream().map(point -> point.priceCentsPerKwh().doubleValue()).toList();
        List<Double> wood = points.stream().map(point -> point.woodRoomHeatingRate().doubleValue()).toList();
        AxisRange temperatureRange = paddedRange(1.0, room, floor, setpoint, outdoor);
        AxisRange priceRange = paddedRange(2.0, price, wood);

        getElement().executeJs("""
                const options = {
                  chart: { height: 380, type: 'line', toolbar: { show: true }, animations: { enabled: false } },
                  series: [
                    { name: 'Room temperature', data: $1, type: 'line' },
                    { name: 'Floor temperature', data: $2, type: 'line' },
                    { name: 'Floor setpoint', data: $3, type: 'line' },
                    { name: 'Electricity price', data: $4, type: 'column' },
                    { name: 'Wood heat effect', data: $5, type: 'area' },
                    { name: 'Outdoor temperature', data: $6, type: 'line' }
                  ],
                  xaxis: { categories: $0, title: { text: 'Local time' } },
                  yaxis: [
                    { title: { text: 'Temperature °C' }, min: $7, max: $8 },
                    { show: false, min: $7, max: $8 },
                    { show: false, min: $7, max: $8 },
                    { opposite: true, title: { text: 'Price c/kWh / wood effect' }, min: $9, max: $10 },
                    { show: false, min: $9, max: $10 },
                    { show: false, min: $7, max: $8 }
                  ],
                  stroke: { width: [3, 3, 2, 0, 2, 2], curve: 'smooth', dashArray: [0, 0, 5, 0, 0, 4] },
                  colors: ['#2f80ed', '#eb5757', '#f2994a', '#9b51e0', '#27ae60', '#00a3a3'],
                  fill: { opacity: [1, 1, 1, 0.25, 0.15, 1] },
                  tooltip: { shared: true },
                  legend: { position: 'top' }
                };
                this.chartInstance = new ApexCharts(this, options);
                this.chartInstance.render();
                """, labels, room, floor, setpoint, price, wood, outdoor, temperatureRange.min(), temperatureRange.max(),
                priceRange.min(), priceRange.max());
    }

    @SafeVarargs
    private final AxisRange paddedRange(double minimumPadding, List<Double>... series) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (List<Double> values : series) {
            for (Double value : values) {
                if (value == null || value.isNaN() || value.isInfinite()) {
                    continue;
                }
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return new AxisRange(0, 1);
        }
        double padding = Math.max(minimumPadding, (max - min) * 0.12);
        return new AxisRange(Math.floor((min - padding) * 10.0) / 10.0,
                Math.ceil((max + padding) * 10.0) / 10.0);
    }

    private record AxisRange(double min, double max) {
    }
}
