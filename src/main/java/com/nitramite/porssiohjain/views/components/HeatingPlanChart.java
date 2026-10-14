/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JavaScript("context://js/apexcharts.min.js")
public class HeatingPlanChart extends Div {

    public HeatingPlanChart(List<HeatingPlanSimulationService.MarketPoint> market,
                            List<HeatingPlanSimulationService.SimulationPoint> predictions,
                            List<HeatingPlanSimulationService.SimulationPoint> history,
                            LocalDate date, ZoneId zone, String nowLabel, Instant calculatedAt) {
        setWidthFull();
        setHeight("390px");
        getStyle().set("position", "relative");

        List<ChartPoint> points = dayPoints(market, predictions, history, date, zone, calculatedAt);
        List<Long> times = points.stream().map(point -> point.time().toEpochMilli()).toList();
        List<Double> room = points.stream().map(ChartPoint::room).toList();
        List<Double> floor = points.stream().map(ChartPoint::floor).toList();
        List<Double> setpoint = points.stream().map(ChartPoint::setpoint).toList();
        List<Double> outdoor = points.stream().map(ChartPoint::outdoor).toList();
        List<Double> price = points.stream().map(ChartPoint::price).toList();
        List<Double> wood = points.stream().map(ChartPoint::wood).toList();
        AxisRange temperatureRange = paddedRange(1.0, room, floor, setpoint, outdoor);
        AxisRange priceRange = paddedRange(2.0, price, wood);

        getElement().executeJs("""
                const times = $0;
                const data = values => values.map((value, index) => [times[index], value]);
                const timeFormat = new Intl.DateTimeFormat('en-GB', {
                  timeZone: $14, hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
                });
                const nowAnnotations = now => ({
                  xaxis: now >= $12 && now < $13 ? [{
                    x: now, borderColor: '#c62828', borderWidth: 2, strokeDashArray: 4,
                    label: {
                      orientation: 'horizontal',
                      style: { color: '#fff', background: '#c62828' },
                      text: $11 + ' ' + timeFormat.format(new Date(now))
                    }
                  }] : []
                });
                const options = {
                  chart: { height: 380, type: 'line', toolbar: { show: true }, animations: { enabled: false } },
                  series: [
                    { name: 'Room temperature (predicted)', data: data($1), type: 'line' },
                    { name: 'Floor temperature (predicted)', data: data($2), type: 'line' },
                    { name: 'Floor setpoint (planned)', data: data($3), type: 'line' },
                    { name: 'Electricity price', data: data($4), type: 'column' },
                    { name: 'Wood heat effect (predicted)', data: data($5), type: 'area' },
                    { name: 'Outdoor temperature (forecast)', data: data($6), type: 'line' }
                  ],
                  xaxis: {
                    type: 'datetime', min: $12, max: $13, title: { text: 'Local time (' + $14 + ')' },
                    labels: { formatter: (value, timestamp) => timeFormat.format(new Date(timestamp ?? Number(value))) }
                  },
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
                  tooltip: { shared: true, x: { formatter: value => timeFormat.format(new Date(value)) } },
                  legend: { position: 'top' },
                  annotations: nowAnnotations(Date.now())
                };
                const chart = new ApexCharts(this, options);
                this.chartInstance = chart;
                chart.render().then(() => {
                  if (!this.isConnected) { chart.destroy(); return; }
                  const timer = window.setInterval(() => {
                    chart.updateOptions({ annotations: nowAnnotations(Date.now()) }, false, false);
                  }, 60000);
                  const observer = new MutationObserver(() => {
                    if (!this.isConnected) {
                      window.clearInterval(timer);
                      observer.disconnect();
                      chart.destroy();
                    }
                  });
                  observer.observe(document, { childList: true, subtree: true });
                });
                """, times, room, floor, setpoint, price, wood, outdoor, temperatureRange.min(), temperatureRange.max(),
                priceRange.min(), priceRange.max(), nowLabel,
                date.atStartOfDay(zone).toInstant().toEpochMilli(),
                date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), zone.getId());
    }

    /** Chart history is display-only; it never enters the live simulation or command plan. */
    static List<ChartPoint> dayPoints(List<HeatingPlanSimulationService.MarketPoint> market,
                                     List<HeatingPlanSimulationService.SimulationPoint> predictions,
                                     List<HeatingPlanSimulationService.SimulationPoint> history,
                                     LocalDate date, ZoneId zone, Instant calculatedAt) {
        Map<Instant, HeatingPlanSimulationService.MarketPoint> prices = new HashMap<>();
        market.forEach(point -> prices.put(point.time(), point));
        Map<Instant, HeatingPlanSimulationService.SimulationPoint> temperatures = new HashMap<>();
        history.stream().filter(point -> !point.time().plusSeconds(900).isAfter(calculatedAt))
                .forEach(point -> temperatures.put(point.time(), point));
        predictions.forEach(point -> temperatures.put(point.time(), point));
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<ChartPoint> points = new ArrayList<>();
        for (Instant time = date.atStartOfDay(zone).toInstant(); time.isBefore(end); time = time.plusSeconds(900)) {
            var price = prices.get(time);
            var prediction = temperatures.get(time);
            points.add(new ChartPoint(time,
                    prediction == null ? null : value(prediction.roomTemperature()),
                    prediction == null ? null : value(prediction.floorTemperature()),
                    prediction == null ? null : value(prediction.floorSetpoint()),
                    price == null ? null : value(price.outdoorTemperature()),
                    price == null ? null : value(price.priceCentsPerKwh()),
                    prediction == null ? null : value(prediction.woodRoomHeatingRate())));
        }
        return points;
    }

    private static Double value(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    record ChartPoint(Instant time, Double room, Double floor, Double setpoint, Double outdoor, Double price, Double wood) { }

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
