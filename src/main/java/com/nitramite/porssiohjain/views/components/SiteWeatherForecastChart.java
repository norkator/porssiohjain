/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastPointResponse;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@JavaScript("context://js/apexcharts.min.js")
public class SiteWeatherForecastChart extends Div {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public SiteWeatherForecastChart(List<SiteWeatherEntity> forecast, ZoneId zone) {
        this(forecast.stream().map(point -> SiteWeatherForecastPointResponse.builder()
                        .time(point.getForecastTime()).temperature(point.getTemperature())
                        .windSpeedMs(point.getWindSpeedMs()).build()).toList(),
                zone, null, new Labels("Outdoor temperature", "Wind speed", "Humidity", "Forecast time", null));
    }

    public SiteWeatherForecastChart(List<SiteWeatherForecastPointResponse> forecast, LocalDate date,
                                    ZoneId zone, Labels labels) {
        this(dayForecast(forecast, date, zone), zone, date, labels);
    }

    private SiteWeatherForecastChart(List<SiteWeatherForecastPointResponse> forecast, ZoneId zone,
                                     LocalDate date, Labels labels) {
        setWidthFull();
        setHeight(date == null ? "300px" : "330px");
        getStyle().set("position", "relative").set("flex-shrink", "0").set("min-width", "0");

        List<?> times = date == null
                ? forecast.stream().map(point -> TIME_FORMAT.format(point.getTime().atZone(zone))).toList()
                : forecast.stream().map(point -> point.getTime().toEpochMilli()).toList();
        List<Double> temperatures = forecast.stream()
                .map(point -> point.getTemperature() == null ? null : point.getTemperature().doubleValue())
                .toList();
        List<Double> wind = forecast.stream()
                .map(point -> point.getWindSpeedMs() == null ? null : point.getWindSpeedMs().doubleValue())
                .toList();
        List<Double> humidity = forecast.stream()
                .map(point -> point.getHumidity() == null ? null : point.getHumidity().doubleValue())
                .toList();
        AxisRange temperatureRange = paddedRange(1.0, temperatures);
        AxisRange windRange = paddedRange(1.0, wind);

        getElement().executeJs("""
                const singleDay = $8 !== null;
                const data = values => singleDay ? values.map((value, index) => [$0[index], value]) : values;
                const timeFormat = new Intl.DateTimeFormat('en-GB', {
                  timeZone: $10, hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
                });
                const nowAnnotations = now => ({
                  xaxis: singleDay && now >= $8 && now < $9 ? [{
                    x: now, borderColor: '#c62828', borderWidth: 2, strokeDashArray: 4,
                    label: {
                      orientation: 'horizontal',
                      style: { color: '#fff', background: '#c62828' },
                      text: $15 + ' ' + timeFormat.format(new Date(now))
                    }
                  }] : []
                });
                const colors = ['#007c89', '#6b7280', '#2563eb'];
                const units = ['°C', 'm/s', '%'];
                const series = [
                  { name: $11 + (singleDay ? ' (°C)' : ''), data: data($1), type: 'line' },
                  { name: $12 + (singleDay ? ' (m/s)' : ''), data: data($2), type: 'column' }
                ];
                const yaxis = [
                  { title: { text: (singleDay ? $11 : 'Temperature') + ' °C' }, min: $4, max: $5 },
                  { opposite: true, title: { text: (singleDay ? $12 : 'Wind') + ' m/s' }, min: $6, max: $7 }
                ];
                if (singleDay) {
                  series.push({ name: $13 + ' (%)', data: data($3), type: 'line' });
                  yaxis.push({ opposite: true, title: { text: $13 + ' %' }, min: 0, max: 100 });
                }
                const options = {
                  chart: { height: singleDay ? 320 : 290, type: 'line', toolbar: { show: true }, animations: { enabled: false } },
                  series,
                  xaxis: singleDay ? {
                    type: 'datetime', min: $8, max: $9, tickAmount: 8, title: { text: $14 + ' (' + $10 + ')' },
                    labels: { formatter: (value, timestamp) => timeFormat.format(new Date(timestamp ?? Number(value))) }
                  } : { categories: $0, title: { text: $14 } },
                  yaxis,
                  stroke: { width: singleDay ? [3, 0, 2] : [3, 0], curve: 'smooth', dashArray: singleDay ? [0, 0, 5] : [0, 0] },
                  colors,
                  fill: { opacity: singleDay ? [1, 0.28, 1] : [1, 0.28] },
                  tooltip: {
                    shared: true,
                    x: singleDay ? { formatter: value => timeFormat.format(new Date(value)) } : {},
                    y: singleDay ? units.map(unit => ({ formatter: value => value == null ? '-' : value.toFixed(1) + ' ' + unit })) : {}
                  },
                  legend: { position: 'top' },
                  annotations: nowAnnotations(Date.now()),
                  responsive: singleDay ? [{
                    breakpoint: 600,
                    options: {
                      chart: { toolbar: { show: false } },
                      grid: { padding: { left: 12, right: 8 } },
                      legend: { fontSize: '11px', itemMargin: { horizontal: 4, vertical: 4 } },
                      xaxis: { tickAmount: 4, labels: { rotate: 0, style: { fontSize: '10px' } },
                        title: { style: { fontSize: '10px' } } },
                      yaxis: yaxis.map((axis, index) => ({
                        ...axis, show: true, opposite: index > 0, title: { text: '' },
                        labels: {
                          minWidth: 28, maxWidth: 32, offsetX: index === 0 ? 24 : 0,
                          style: { fontSize: '10px', colors: [colors[index]] },
                          formatter: value => Number.isFinite(value) ? value.toFixed(index === 2 ? 0 : 1) : ''
                        }
                      }))
                    }
                  }] : []
                };
                const chart = new ApexCharts(this, options);
                this.chartInstance = chart;
                chart.render().then(() => {
                  if (!this.isConnected) { chart.destroy(); return; }
                  const timer = singleDay ? window.setInterval(() => {
                    chart.updateOptions({ annotations: nowAnnotations(Date.now()) }, false, false);
                  }, 60000) : null;
                  const observer = new MutationObserver(() => {
                    if (!this.isConnected) {
                      if (timer !== null) window.clearInterval(timer);
                      observer.disconnect();
                      chart.destroy();
                    }
                  });
                  observer.observe(document, { childList: true, subtree: true });
                });
                """, times, temperatures, wind, humidity, temperatureRange.min(), temperatureRange.max(),
                windRange.min(), windRange.max(),
                date == null ? null : date.atStartOfDay(zone).toInstant().toEpochMilli(),
                date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                zone.getId(), labels.temperature(), labels.windSpeed(), labels.humidity(), labels.time(), labels.now());
    }

    static List<SiteWeatherForecastPointResponse> dayForecast(List<SiteWeatherForecastPointResponse> forecast,
                                                             LocalDate date, ZoneId zone) {
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        return forecast.stream()
                .filter(point -> point.getTime() != null && !point.getTime().isBefore(start) && point.getTime().isBefore(end))
                .sorted(Comparator.comparing(SiteWeatherForecastPointResponse::getTime))
                .toList();
    }

    public record Labels(String temperature, String windSpeed, String humidity, String time, String now) { }

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
