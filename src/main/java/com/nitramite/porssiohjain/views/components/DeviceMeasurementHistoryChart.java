/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.entity.DeviceMeasurementEntity;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;

import java.time.ZoneId;
import java.time.Instant;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;

@JavaScript("context://js/apexcharts.min.js")
public class DeviceMeasurementHistoryChart extends Div {

    public DeviceMeasurementHistoryChart(List<DeviceMeasurementEntity> history, ZoneId zone,
                                        String seriesName, String unit) {
        this(history, zone, seriesName, unit, "Report / cloud snapshot time", "No history values available.", "en-GB");
    }

    public DeviceMeasurementHistoryChart(List<DeviceMeasurementEntity> history, ZoneId zone,
                                        String seriesName, String unit, String timeLabel, String noDataLabel,
                                        String locale) {
        this(history.stream().sorted(Comparator.comparing(DeviceMeasurementEntity::getObservedAt)
                        .thenComparing(DeviceMeasurementEntity::getId))
                        .map(row -> new HistoryPoint(row.getObservedAt(), row.getValue().doubleValue())).toList(),
                zone, seriesName, unit, timeLabel, noDataLabel, locale, null, null);
    }

    public static DeviceMeasurementHistoryChart publicHistory(List<HistoryPoint> history, ZoneId zone,
            String seriesName, String unit, String timeLabel, String noDataLabel, String locale,
            Instant from, Instant to) {
        return new DeviceMeasurementHistoryChart(history, zone, seriesName, unit, timeLabel, noDataLabel, locale, from, to);
    }

    private DeviceMeasurementHistoryChart(List<HistoryPoint> history, ZoneId zone,
            String seriesName, String unit, String timeLabel, String noDataLabel, String locale,
            Instant from, Instant to) {
        setWidthFull();
        setHeight("300px");
        getStyle().set("position", "relative").set("flex-shrink", "0").set("min-width", "0");

        List<List<Number>> points = history.stream()
                .sorted(Comparator.comparing(HistoryPoint::observedAt))
                .map(point -> List.<Number>of(point.observedAt().toEpochMilli(), point.value()))
                .toList();
        DoubleSummaryStatistics values = history.stream()
                .mapToDouble(HistoryPoint::value)
                .summaryStatistics();
        double padding = Math.max(0.1, (values.getMax() - values.getMin()) * 0.12);
        double min = history.isEmpty() ? 0 : Math.floor((values.getMin() - padding) * 10.0) / 10.0;
        double max = history.isEmpty() ? 1 : Math.ceil((values.getMax() + padding) * 10.0) / 10.0;

        getElement().executeJs("""
                const data = $0;
                const hasData = data.length > 0;
                const singleTime = hasData && data[0][0] === data[data.length - 1][0];
                const timeFormat = new Intl.DateTimeFormat($8, {
                  timeZone: $4, month: '2-digit', day: '2-digit',
                  hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
                });
                const tooltipTimeFormat = new Intl.DateTimeFormat($8, {
                  timeZone: $4, year: 'numeric', month: '2-digit', day: '2-digit',
                  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
                });
                const options = {
                  chart: { width: '100%', height: 290, type: 'line', toolbar: { show: hasData }, animations: { enabled: false } },
                  series: [{ name: $1, data: data, type: 'line' }],
                  xaxis: {
                    type: 'datetime', title: { text: hasData ? $6 : undefined },
                    min: $9 ?? (singleTime ? data[0][0] - 15 * 60 * 1000 : undefined),
                    max: $10 ?? (singleTime ? data[0][0] + 15 * 60 * 1000 : undefined),
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
                  noData: { text: $7 }
                };
                this.chartInstance = new ApexCharts(this, options);
                const chart = this.chartInstance;
                chart.render().then(() => {
                  let lastWidth = 0;
                  const resizeObserver = new ResizeObserver(entries => {
                    const width = Math.floor(entries[0].contentRect.width);
                    if (this.isConnected && width > 0 && width !== lastWidth) {
                      lastWidth = width;
                      chart.updateOptions({ chart: { width } }, false, false);
                    }
                  });
                  resizeObserver.observe(this);
                  const detached = new MutationObserver(() => {
                    if (!this.isConnected) {
                      resizeObserver.disconnect();
                      detached.disconnect();
                      chart.destroy();
                    }
                  });
                  detached.observe(document.body, { childList: true, subtree: true });
                });
                """, points, seriesName, min, max, zone.getId(), unit, timeLabel, noDataLabel, locale,
                from == null ? null : from.toEpochMilli(), to == null ? null : to.toEpochMilli());
    }

    public record HistoryPoint(Instant observedAt, double value) {}
}
