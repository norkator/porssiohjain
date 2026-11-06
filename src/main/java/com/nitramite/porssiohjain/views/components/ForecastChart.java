/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.views.components;

import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@JavaScript("context://js/apexcharts.min.js")
@JsModule("./forecast-chart.js")
public class ForecastChart extends Div {

    public ForecastChart(List<Instant> times, List<BigDecimal> values, Instant end, ZoneId zone,
                         Locale locale, String seriesName, String unit, String nowLabel, boolean prices) {
        addClassName("forecast-chart");
        setWidthFull();
        setHeight("350px");
        getElement().setAttribute("role", "img");
        getElement().setAttribute("aria-label", seriesName + " (" + unit + ")");
        if (times.isEmpty()) return;

        List<DayBand> bands = dayBands(times.getFirst(), end, zone);
        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("EEE d.M.", locale);
        getElement().executeJs("""
                window.renderForecastChart(this, {
                  times: $0, values: $1, end: $2, zone: $3, locale: $4,
                  name: $5, unit: $6, nowLabel: $7, prices: $8,
                  days: $9.map((start, index) => ({ start, end: $10[index], label: $11[index] }))
                });
                """, times.stream().map(Instant::toEpochMilli).toList(),
                values.stream().map(BigDecimal::doubleValue).toList(), end.toEpochMilli(), zone.getId(),
                locale.toLanguageTag(), seriesName, unit, nowLabel, prices,
                bands.stream().map(band -> band.start().toEpochMilli()).toList(),
                bands.stream().map(band -> band.end().toEpochMilli()).toList(),
                bands.stream().map(band -> dayFormat.format(band.date())).toList());
    }

    static List<DayBand> dayBands(Instant start, Instant end, ZoneId zone) {
        List<DayBand> bands = new ArrayList<>();
        for (LocalDate day = start.atZone(zone).toLocalDate(); day.atStartOfDay(zone).toInstant().isBefore(end);
             day = day.plusDays(1)) {
            Instant dayStart = day.atStartOfDay(zone).toInstant();
            Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
            bands.add(new DayBand(day, dayStart.isBefore(start) ? start : dayStart,
                    dayEnd.isAfter(end) ? end : dayEnd));
        }
        return bands;
    }

    record DayBand(LocalDate date, Instant start, Instant end) { }
}
