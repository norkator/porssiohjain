/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;

@JavaScript("context://js/apexcharts.min.js")
public class ZigbeeMeasurementHistoryChart extends Div {

    public ZigbeeMeasurementHistoryChart(List<ZigbeeDeviceMeasurementEntity> history, ZoneId zone,
                                        String seriesName, String unit) {
        setWidthFull();
        setHeight("300px");
        getStyle().set("position", "relative").set("flex-shrink", "0");

        List<List<Number>> points = history.stream()
                .sorted(Comparator.comparing(ZigbeeDeviceMeasurementEntity::getMeasuredAt)
                        .thenComparing(ZigbeeDeviceMeasurementEntity::getId))
                .map(point -> List.<Number>of(point.getMeasuredAt().toEpochMilli(), point.getValue().doubleValue()))
                .toList();
        DoubleSummaryStatistics values = history.stream()
                .mapToDouble(point -> point.getValue().doubleValue())
                .summaryStatistics();
        double padding = Math.max(0.1, (values.getMax() - values.getMin()) * 0.12);
        double min = history.isEmpty() ? 0 : Math.floor((values.getMin() - padding) * 10.0) / 10.0;
        double max = history.isEmpty() ? 1 : Math.ceil((values.getMax() + padding) * 10.0) / 10.0;

        getElement().executeJs("""
                const data = $0;
                const hasData = data.length > 0;
                const singleTime = hasData && data[0][0] === data[data.length - 1][0];
                const timeFormat = new Intl.DateTimeFormat('en-GB', {
                  timeZone: $4, month: '2-digit', day: '2-digit',
                  hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
                });
                const tooltipTimeFormat = new Intl.DateTimeFormat('en-GB', {
                  timeZone: $4, year: 'numeric', month: '2-digit', day: '2-digit',
                  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
                });
                const options = {
                  chart: { height: 290, type: 'line', toolbar: { show: hasData }, animations: { enabled: false } },
                  series: [{ name: $1, data: data, type: 'line' }],
                  xaxis: {
                    type: 'datetime', title: { text: hasData ? 'Measured time' : undefined },
                    min: singleTime ? data[0][0] - 15 * 60 * 1000 : undefined,
                    max: singleTime ? data[0][0] + 15 * 60 * 1000 : undefined,
                    axisBorder: { show: hasData }, axisTicks: { show: hasData },
                    labels: {
                      show: hasData,
                      formatter: (value, timestamp) => timeFormat.format(new Date(timestamp ?? Number(value)))
                    }
                  },
                  yaxis: {
                    show: hasData, title: { text: $1 + ' ' + $5 }, min: $2, max: $3,
                    labels: { formatter: value => Number(value.toFixed(2)).toString() }
                  },
                  grid: { show: hasData },
                  stroke: { width: data.length === 1 ? 0 : 3, curve: 'smooth' },
                  colors: ['#007c89'],
                  fill: { opacity: 1 },
                  markers: { size: data.length === 1 ? 4 : 0, hover: { size: 5 } },
                  tooltip: {
                    shared: true,
                    x: { formatter: value => tooltipTimeFormat.format(new Date(value)) },
                    y: { formatter: value => Number(value.toFixed(3)) + ' ' + $5 }
                  },
                  legend: { show: hasData, position: 'top', showForSingleSeries: true },
                  noData: { text: 'No history values available.' }
                };
                this.chartInstance = new ApexCharts(this, options);
                this.chartInstance.render();
                """, points, seriesName, min, max, zone.getId(), unit);
    }
}
