/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse.*;
import com.nitramite.porssiohjain.views.components.HeatPumpControlPreviewChart;
import com.nitramite.porssiohjain.views.components.InfoBox;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class HeatPumpControlPreviewDialog extends Dialog {
    private static final String PREFIX = "controlTable.heatPumpPreview.";
    private final HeatPumpControlPreviewResponse preview;
    private final I18nService i18n;
    private final Consumer<String> showState;
    private final ZoneId zone;
    private final ComboBox<Pump> pump = new ComboBox<>();
    private final ComboBox<LocalDate> day = new ComboBox<>();
    private final VerticalLayout content = new VerticalLayout();

    public HeatPumpControlPreviewDialog(HeatPumpControlPreviewResponse preview, I18nService i18n,
                                        Consumer<String> showState) {
        this.preview = preview;
        this.i18n = i18n;
        this.showState = showState;
        zone = ZoneId.of(preview.timezone());
        setHeaderTitle(t("title"));
        setWidth("1200px");
        setMaxWidth("96vw");
        setMaxHeight("92dvh");
        Paragraph timing = new Paragraph(t("timing", format(preview.generatedAt()), preview.timezone()));
        timing.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-s)");
        pump.setLabel(t("pump"));
        pump.setItems(preview.pumps());
        pump.setItemLabelGenerator(Pump::name);
        day.setLabel(t("day"));
        LocalDate today = preview.generatedAt().atZone(zone).toLocalDate();
        day.setItems(today, today.plusDays(1));
        day.setItemLabelGenerator(date -> t(date.equals(today) ? "today" : "tomorrow") + " · " + date);
        day.setValue(today);
        HorizontalLayout selectors = new HorizontalLayout(pump, day);
        selectors.setWidthFull();
        selectors.setWrap(true);
        selectors.setFlexGrow(1, pump, day);
        content.setPadding(false);
        content.setWidthFull();
        content.getStyle().set("min-width", "0").set("flex-shrink", "0");
        VerticalLayout body = new VerticalLayout(selectors, timing, content);
        body.setPadding(false);
        body.getStyle().set("min-width", "0");
        add(body);
        getFooter().add(new Button(i18n.t("common.close"), event -> close()));
        pump.addValueChangeListener(event -> render());
        day.addValueChangeListener(event -> render());
        if (preview.pumps().isEmpty()) content.add(new Paragraph(t("empty")));
        else pump.setValue(preview.pumps().getFirst());
    }

    private void render() {
        content.removeAll();
        Pump selected = pump.getValue();
        if (selected == null || day.getValue() == null) return;
        if (selected.competingRules()) content.add(new InfoBox(t("otherRulesTitle"), t("otherRules")));
        LocalDate date = day.getValue();
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<Period> periods = selected.periods().stream()
                .filter(period -> period.end().isAfter(start) && period.start().isBefore(end)).toList();
        List<PricePeriod> prices = preview.prices().stream()
                .filter(price -> price.end().isAfter(start) && price.start().isBefore(end)).toList();
        if (prices.stream().anyMatch(price -> price.price() == null)) content.add(new Paragraph(t("missingPrices")));
        if (periods.stream().anyMatch(period -> period.reason() == Reason.DISABLED)) content.add(new Paragraph(t("reason.DISABLED")));
        if (periods.stream().anyMatch(period -> period.reason() == Reason.NO_AC_DATA)) content.add(new Paragraph(t("reason.NO_AC_DATA")));
        content.add(new HeatPumpControlPreviewChart(prices, periods.stream().map(period ->
                new HeatPumpControlPreviewChart.PlotPeriod(period.start().toEpochMilli(), period.end().toEpochMilli(),
                        period.state() == null ? null : period.state().power(),
                        period.state() == null ? null : period.state().temperature(),
                        period.state() == null ? null : period.state().mode(), period.assumed(), summary(period), reason(period)))
                .toList(), date, zone, preview.generatedAt(), i18n));
        Details explanation = new Details(t("explanation"),
                new Paragraph(t("description")), new Paragraph(t("legend")));
        explanation.setWidthFull();
        content.add(explanation);

        Grid<Period> changes = new Grid<>(Period.class, false);
        changes.setItems(periods);
        changes.setAllRowsVisible(true);
        changes.setMaxHeight("320px");
        changes.addColumn(period -> format(period.start().isBefore(start) ? start : period.start()))
                .setHeader(t("from")).setAutoWidth(true).setFlexGrow(0);
        changes.addColumn(period -> format(period.end().isAfter(end) ? end : period.end()))
                .setHeader(t("until")).setAutoWidth(true).setFlexGrow(0);
        changes.addColumn(this::summary).setHeader(t("settings")).setAutoWidth(true);
        changes.addColumn(this::reason).setHeader(t("reason")).setAutoWidth(true);
        changes.addComponentColumn(period -> {
            Button details = new Button(t("details"), event -> showState.accept(period.stateHex()));
            details.setEnabled(period.stateHex() != null);
            return details;
        }).setHeader(t("details")).setAutoWidth(true).setFlexGrow(0);
        changes.addThemeNames("wrap-cell-content");
        content.add(changes);
    }

    private String summary(Period period) {
        State state = period.state();
        String result = state == null ? t("unknown") : Boolean.FALSE.equals(state.power()) ? t("off")
                : (Boolean.TRUE.equals(state.power()) ? t("on") : t("unknown"))
                + (state.mode() == null ? "" : " · " + mode(state.mode()))
                + (state.temperature() == null ? "" : " · " + state.temperature() + " °C")
                + (state.fan() == null ? "" : " · " + t("fan", state.fan()))
                + (state.swing() == null ? "" : " · " + t("swing", state.swing()));
        return period.assumed() && state != null ? result + " · " + t("assumed") : result;
    }

    private String mode(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "heat", "heating" -> t("mode.heat");
            case "cool", "cooling" -> t("mode.cool");
            case "dry" -> t("mode.dry");
            case "fan", "fan only" -> t("mode.fan");
            case "auto", "automatic" -> t("mode.auto");
            default -> value;
        };
    }

    private String reason(Period period) {
        if (period.reason() == Reason.PRICE) return t("reason.PRICE", period.ruleId(),
                period.comparison() == com.nitramite.porssiohjain.entity.enums.ComparisonType.LESS_THAN ? "<" : ">",
                period.threshold());
        if (period.reason() == Reason.SCHEDULE) return t("reason.SCHEDULE", period.ruleId());
        return t("reason." + period.reason().name());
    }

    private String format(Instant time) {
        return DateTimeFormatter.ofPattern("dd.MM HH:mm XXX").format(time.atZone(zone));
    }

    private String t(String key, Object... args) {
        return i18n.t(PREFIX + key, args);
    }
}
