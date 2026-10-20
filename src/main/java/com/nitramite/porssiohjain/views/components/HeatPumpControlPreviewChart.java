/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */
package com.nitramite.porssiohjain.views.components;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse.PricePeriod;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JavaScript("context://js/apexcharts.min.js")
@JavaScript("context://js/heat-pump-control-preview.js")
public class HeatPumpControlPreviewChart extends Div {
    public HeatPumpControlPreviewChart(List<PricePeriod> prices, List<PlotPeriod> periods, LocalDate day,
                                       ZoneId zone, Instant generatedAt, I18nService i18n) {
        setWidthFull();
        setHeight("clamp(320px, 52dvh, 460px)");
        getStyle().set("min-width", "0").set("flex-shrink", "0").set("display", "block");
        getElement().setAttribute("aria-label", i18n.t("controlTable.heatPumpPreview.title"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "heat-pump-preview-" + UUID.randomUUID());
        data.put("start", day.atStartOfDay(zone).toInstant().toEpochMilli());
        data.put("end", day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli());
        data.put("now", generatedAt.toEpochMilli());
        data.put("timezone", zone.getId());
        data.put("prices", prices.stream().map(price -> new Object[]{price.start().toEpochMilli(),
                price.end().toEpochMilli(), price.price()}).toList());
        data.put("periods", periods);
        Map<String, String> labels = new LinkedHashMap<>();
        for (String key : List.of("price", "priceUnit", "temperature", "power", "on", "off", "time", "assumed")) {
            labels.put(key, i18n.t("controlTable.heatPumpPreview." + key));
        }
        for (String mode : List.of("heat", "cool", "fan", "dry", "auto")) {
            labels.put(mode, i18n.t("controlTable.heatPumpPreview.mode." + mode));
        }
        data.put("labels", labels);
        try {
            getElement().executeJs("window.renderHeatPumpControlPreview(this, JSON.parse($0))",
                    new ObjectMapper().writeValueAsString(data));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not render heat pump preview", e);
        }
    }

    public record PlotPeriod(long start, long end, Boolean power, Double temperature, String mode,
                             boolean assumed, String summary, String reason) { }
}
