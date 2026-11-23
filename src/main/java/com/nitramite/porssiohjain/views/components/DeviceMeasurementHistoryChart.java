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
                zone, seriesName, unit, timeLabel, noDataLabel, locale, null, null, false);
    }

    public static DeviceMeasurementHistoryChart history(List<HistoryPoint> history, ZoneId zone,
            String seriesName, String unit, String timeLabel, String noDataLabel, String locale) {
        return new DeviceMeasurementHistoryChart(history, zone, seriesName, unit, timeLabel, noDataLabel, locale, null, null, false);
    }

    public static DeviceMeasurementHistoryChart publicHistory(List<HistoryPoint> history, ZoneId zone,
            String seriesName, String unit, String timeLabel, String noDataLabel, String locale,
            Instant from, Instant to, boolean compact) {
        return new DeviceMeasurementHistoryChart(history, zone, seriesName, unit, timeLabel, noDataLabel, locale, from, to, compact);
    }

    public static DeviceMeasurementHistoryChart estimatedHistory(List<HistoryPoint> history, List<HistoryPoint> estimates,
            ZoneId zone, String seriesName, String estimateName, String unit, String timeLabel, String noDataLabel, String locale) {
        return new DeviceMeasurementHistoryChart(history, zone, seriesName, unit, timeLabel, noDataLabel, locale,
                null, null, false, estimates, estimateName);
    }

    private DeviceMeasurementHistoryChart(List<HistoryPoint> history, ZoneId zone,
            String seriesName, String unit, String timeLabel, String noDataLabel, String locale,
            Instant from, Instant to, boolean compact) {
        this(history, zone, seriesName, unit, timeLabel, noDataLabel, locale, from, to, compact, List.of(), "");
    }

    private DeviceMeasurementHistoryChart(List<HistoryPoint> history, ZoneId zone,
            String seriesName, String unit, String timeLabel, String noDataLabel, String locale,
            Instant from, Instant to, boolean compact, List<HistoryPoint> estimates, String estimateName) {
        setWidthFull();
        setHeight(compact ? "200px" : "300px");
        getStyle().set("position", "relative").set("flex-shrink", "0").set("min-width", "0");

        List<List<Number>> points = history.stream()
                .sorted(Comparator.comparing(HistoryPoint::observedAt))
                .map(point -> List.<Number>of(point.observedAt().toEpochMilli(), point.value()))
                .toList();
        List<List<Number>> estimatedPoints = estimates.stream().sorted(Comparator.comparing(HistoryPoint::observedAt))
                .map(point -> List.<Number>of(point.observedAt().toEpochMilli(), point.value())).toList();
        DoubleSummaryStatistics values = java.util.stream.Stream.concat(history.stream(), estimates.stream())
                .mapToDouble(HistoryPoint::value)
                .summaryStatistics();
        double padding = Math.max(0.1, (values.getMax() - values.getMin()) * 0.12);
        double min = values.getCount() == 0 ? 0 : Math.floor((values.getMin() - padding) * 10.0) / 10.0;
        double max = values.getCount() == 0 ? 1 : Math.ceil((values.getMax() + padding) * 10.0) / 10.0;

        getElement().executeJs("""
                const data = $0;
                const compact = $11;
                const estimates = $12;
                const allData = [...data, ...estimates].sort((a, b) => a[0] - b[0]);
                const times = [...new Set(allData.map(point => point[0]))];
                const align = points => {
                  const byTime = new Map(points);
                  return times.map(at => [at, byTime.get(at) ?? null]);
                };
                const hasData = allData.length > 0;
                const singleTime = hasData && allData[0][0] === allData[allData.length - 1][0];
                const timeFormat = new Intl.DateTimeFormat($8, {
                  timeZone: $4, ...(compact ? {} : { month: '2-digit', day: '2-digit' }),
                  hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
                });
                const tooltipTimeFormat = new Intl.DateTimeFormat($8, {
                  timeZone: $4, year: 'numeric', month: '2-digit', day: '2-digit',
                  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
                });
                const options = {
                  chart: { width: '100%', height: compact ? 190 : 290, type: 'line',
                    toolbar: { show: hasData && !compact }, animations: { enabled: false } },
                  series: [{ name: $1, data: estimates.length ? align(data) : data, type: 'line' },
                    ...(estimates.length ? [{ name: $13, data: align(estimates), type: 'line' }] : [])],
                  xaxis: {
                    type: 'datetime', title: { text: hasData && !compact ? $6 : undefined },
                    min: $9 ?? (singleTime ? allData[0][0] - 15 * 60 * 1000 : undefined),
                    max: $10 ?? (singleTime ? allData[0][0] + 15 * 60 * 1000 : undefined),
                    axisBorder: { show: hasData }, axisTicks: { show: hasData },
                    labels: {
                      show: hasData,
                      rotate: compact ? 0 : -45,
                      style: { fontSize: compact ? '10px' : '12px' },
                      formatter: (value, timestamp) => timeFormat.format(new Date(timestamp ?? Number(value)))
                    }
                  },
                  yaxis: {
                    show: hasData, title: { text: compact ? $5 : $1 + ' ' + $5 }, min: $2, max: $3,
                    tickAmount: compact ? 4 : undefined,
                    labels: { style: { fontSize: compact ? '10px' : '12px' },
                      formatter: value => Number(value.toFixed(2)).toString() }
                  },
                  grid: { show: hasData },
                  stroke: { width: estimates.length ? [data.length === 1 ? 0 : 3, 2] : data.length === 1 ? 0 : compact ? 2 : 3,
                    curve: estimates.length ? 'straight' : 'smooth', dashArray: estimates.length ? [0, 6] : 0 },
                  colors: ['#007c89', 'rgba(0, 124, 137, 0.4)'],
                  fill: { opacity: 1 },
                  markers: { size: estimates.length ? [data.length === 1 ? 4 : 0, 0] : data.length === 1 ? 4 : 0, hover: { size: 5 } },
                  tooltip: {
                    shared: true,
                    x: { formatter: value => tooltipTimeFormat.format(new Date(value)) },
                    y: { formatter: value => Number(value.toFixed(3)) + ' ' + $5 }
                  },
                  legend: { show: hasData && !compact, position: 'top', showForSingleSeries: true },
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
                from == null ? null : from.toEpochMilli(), to == null ? null : to.toEpochMilli(), compact, estimatedPoints, estimateName);
    }

    public record HistoryPoint(Instant observedAt, double value) {}
}
