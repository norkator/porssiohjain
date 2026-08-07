/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.vaadin.flow.component.html.Div;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SiteWeatherForecastChart extends Div {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public SiteWeatherForecastChart(List<SiteWeatherEntity> forecast, ZoneId zone) {
        setWidthFull();
        setHeight("300px");
        getStyle().set("position", "relative");

        List<String> labels = forecast.stream()
                .map(point -> TIME_FORMAT.format(point.getForecastTime().atZone(zone)))
                .toList();
        List<Double> temperatures = forecast.stream()
                .map(point -> point.getTemperature() == null ? null : point.getTemperature().doubleValue())
                .toList();
        List<Double> wind = forecast.stream()
                .map(point -> point.getWindSpeedMs() == null ? null : point.getWindSpeedMs().doubleValue())
                .toList();
        AxisRange temperatureRange = paddedRange(1.0, temperatures);
        AxisRange windRange = paddedRange(1.0, wind);

        getElement().executeJs("""
                const options = {
                  chart: { height: 290, type: 'line', toolbar: { show: true }, animations: { enabled: false } },
                  series: [
                    { name: 'Outdoor temperature', data: $1, type: 'line' },
                    { name: 'Wind speed', data: $2, type: 'column' }
                  ],
                  xaxis: { categories: $0, title: { text: 'Forecast time' } },
                  yaxis: [
                    { title: { text: 'Temperature °C' }, min: $3, max: $4 },
                    { opposite: true, title: { text: 'Wind m/s' }, min: $5, max: $6 }
                  ],
                  stroke: { width: [3, 0], curve: 'smooth' },
                  colors: ['#007c89', '#6b7280'],
                  fill: { opacity: [1, 0.28] },
                  tooltip: { shared: true },
                  legend: { position: 'top' }
                };
                this.chartInstance = new ApexCharts(this, options);
                this.chartInstance.render();
                """, labels, temperatures, wind, temperatureRange.min(), temperatureRange.max(),
                windRange.min(), windRange.max());
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
