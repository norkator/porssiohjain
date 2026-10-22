/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.*;
import com.nitramite.porssiohjain.services.models.ControlHeatPumpRulesRequest.Rule;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse.*;
import com.nitramite.porssiohjain.views.components.HeatPumpControlPreviewChart;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.timepicker.TimePicker;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

/** One pump, its captured actions, and the resulting graph in one place. */
public class HeatPumpRuleSetDialog extends Dialog {
    private final Long accountId;
    private final Long controlId;
    private final String timezone;
    private final ControlService controls;
    private final HeatPumpControlPreviewService previews;
    private final HeatPumpStateDialogService states;
    private final I18nService i18n;
    private final List<ControlHeatPumpResponse> existing;
    private final List<Row> rows = new ArrayList<>();
    private final ComboBox<DeviceResponse> pump = new ComboBox<>();
    private final Checkbox timed = new Checkbox();
    private final TimePicker from = new TimePicker();
    private final TimePicker until = new TimePicker();
    private final VerticalLayout ruleList = new VerticalLayout();
    private final VerticalLayout fallback = new VerticalLayout();
    private final VerticalLayout graph = new VerticalLayout();
    private final Paragraph error = new Paragraph();
    private Row outside;
    private boolean loading;

    public HeatPumpRuleSetDialog(Long accountId, Long controlId, String timezone, Long deviceId,
                                 List<DeviceResponse> devices, List<ControlHeatPumpResponse> existing,
                                 ControlService controls, HeatPumpControlPreviewService previews,
                                 HeatPumpStateDialogService states, I18nService i18n, Runnable saved) {
        this.accountId = accountId;
        this.controlId = controlId;
        this.timezone = timezone;
        this.controls = controls;
        this.previews = previews;
        this.states = states;
        this.i18n = i18n;
        this.existing = existing;
        setHeaderTitle(t("title"));
        setWidth("1280px");
        setMaxWidth("96vw");
        setMaxHeight("94dvh");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(false);
        pump.setLabel(t("pump"));
        pump.setItems(devices);
        pump.setItemLabelGenerator(DeviceResponse::getDeviceName);
        pump.setWidthFull();
        timed.setLabel(t("timed"));
        from.setLabel(t("from"));
        until.setLabel(t("until"));
        from.setStep(Duration.ofMinutes(1));
        until.setStep(Duration.ofMinutes(1));
        from.setValue(LocalTime.of(7, 0));
        until.setValue(LocalTime.of(22, 0));
        FormLayout window = new FormLayout(from, until);
        window.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        window.setVisible(false);
        ruleList.setPadding(false);
        fallback.setPadding(false);
        fallback.setVisible(false);
        graph.setPadding(false);
        graph.setWidthFull();
        graph.addClassName("heat-pump-rule-preview");
        graph.getStyle().set("min-width", "0").set("flex", "2 1 500px");
        Button addRule = new Button(t("addRule"), event -> {
            rows.add(new Row(null, false));
            changed();
            renderRows();
        });
        VerticalLayout editor = new VerticalLayout(pump, new Paragraph(t("order")), ruleList, addRule,
                timed, window, new Paragraph(t("timezone", timezone)), fallback, new Paragraph(t("captureHelp")));
        editor.addClassName("heat-pump-rule-editor");
        editor.setPadding(false);
        editor.getStyle().set("flex", "1 1 360px").set("min-width", "0");
        HorizontalLayout columns = new HorizontalLayout(editor, graph);
        columns.setWidthFull();
        columns.setAlignItems(FlexComponent.Alignment.START);
        columns.setWrap(true);
        error.getStyle().set("color", "var(--lumo-error-text-color)");
        add(new Paragraph(t("description")), columns, error);
        pump.addValueChangeListener(event -> loadPump());
        timed.addValueChangeListener(event -> {
            window.setVisible(timed.getValue());
            fallback.setVisible(timed.getValue());
            changed();
        });
        from.addValueChangeListener(event -> changed());
        until.addValueChangeListener(event -> changed());
        Button save = new Button(t("save"), event -> {
            try {
                if (pump.getValue() == null) throw new IllegalArgumentException();
                controls.saveHeatPumpRules(accountId, controlId, pump.getValue().getId(), request());
                DesktopFormState.saved(this);
                saved.run();
                close();
            } catch (IllegalArgumentException ex) { error.setText(t("invalid")); }
            catch (Exception ex) { error.setText(t("saveError")); }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button remove = new Button(t("removePump"), event -> {
            Dialog confirmation = new Dialog();
            confirmation.setHeaderTitle(t("removePump"));
            confirmation.add(new Paragraph(t("confirmRemove")));
            confirmation.getFooter().add(new Button(t("removePump"), confirmed -> {
                try {
                    if (pump.getValue() == null) return;
                    controls.saveHeatPumpRules(accountId, controlId, pump.getValue().getId(),
                            new ControlHeatPumpRulesRequest(null, null, List.of()));
                    DesktopFormState.saved(this);
                    saved.run();
                    confirmation.close();
                    close();
                } catch (Exception ex) { error.setText(t("invalid")); confirmation.close(); }
            }), new Button(i18n.t("common.cancel"), ignored -> confirmation.close()));
            confirmation.open();
        });
        remove.setVisible(deviceId != null);
        getFooter().add(remove, new Button(i18n.t("common.cancel"), event -> {
            DesktopFormState.saved(this);
            close();
        }), save);
        pump.setValue(devices.stream().filter(device -> device.getId().equals(deviceId)).findFirst().orElse(null));
        // Existing sets keep their pump identity. Selecting a linked pump when adding opens that set.
        pump.setReadOnly(deviceId != null);
        if (deviceId == null) loadPump();
    }

    private void loadPump() {
        loading = true;
        rows.clear();
        var selected = existing.stream().filter(row -> pump.getValue() != null
                && row.getDeviceId().equals(pump.getValue().getId()))
                .sorted(Comparator.comparing(row -> row.getRuleOrder() == null ? row.getId() : row.getRuleOrder().longValue()))
                .toList();
        timed.setValue(!selected.isEmpty() && selected.getFirst().getActiveFrom() != null);
        if (timed.getValue()) {
            from.setValue(selected.getFirst().getActiveFrom());
            until.setValue(selected.getFirst().getActiveUntil());
        }
        selected.stream().filter(row -> !row.isOutsideActiveTime()).forEach(row -> rows.add(new Row(row, false)));
        if (rows.isEmpty()) rows.add(new Row(null, false));
        outside = new Row(selected.stream().filter(ControlHeatPumpResponse::isOutsideActiveTime).findFirst().orElse(null), true);
        fallback.removeAll();
        fallback.add(new Paragraph(t("outside")), outside.layout);
        renderRows();
        loading = false;
        DesktopFormState.saved(this);
        refreshPreview();
    }

    private void renderRows() {
        ruleList.removeAll();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int index = i;
            Button up = new Button("↑", event -> { Collections.swap(rows, index, index - 1); renderRows(); changed(); });
            up.setAriaLabel(t("moveUp"));
            up.setEnabled(i > 0);
            Button down = new Button("↓", event -> { Collections.swap(rows, index, index + 1); renderRows(); changed(); });
            down.setAriaLabel(t("moveDown"));
            down.setEnabled(i < rows.size() - 1);
            Button remove = new Button(t("removeRule"), event -> { rows.remove(row); renderRows(); changed(); });
            remove.setEnabled(rows.size() > 1);
            VerticalLayout card = new VerticalLayout(new HorizontalLayout(new Paragraph(t("rule", i + 1)), up, down, remove), row.layout);
            card.getStyle().set("background", "var(--lumo-contrast-5pct)").set("border-radius", "8px");
            ruleList.add(card);
        }
    }

    private ControlHeatPumpRulesRequest request() {
        List<Rule> values = new ArrayList<>();
        rows.forEach(row -> values.add(row.value()));
        if (timed.getValue()) values.add(outside.value());
        return new ControlHeatPumpRulesRequest(timed.getValue() ? from.getValue() : null,
                timed.getValue() ? until.getValue() : null, values);
    }

    private void changed() {
        if (loading) return;
        DesktopFormState.markDirty(this);
        refreshPreview();
    }

    private void refreshPreview() {
        graph.removeAll();
        error.setText("");
        if (pump.getValue() == null || rows.stream().anyMatch(row -> row.state.isEmpty())
                || timed.getValue() && outside.state.isEmpty()) {
            graph.add(new Paragraph(t("captureHint")));
            return;
        }
        try {
            var preview = previews.previewDraft(accountId, controlId, pump.getValue().getId(), request());
            var selected = preview.pumps().stream().filter(value -> value.id().equals(pump.getValue().getId())).findFirst().orElseThrow();
            graph.add(new Paragraph(t("preview")), new Paragraph(t("previewHelp")));
            if (selected.competingRules()) graph.add(new Paragraph(i18n.t("controlTable.heatPumpPreview.otherRules")));
            ZoneId zone = ZoneId.of(timezone);
            LocalDate today = preview.generatedAt().atZone(zone).toLocalDate();
            for (LocalDate date : List.of(today, today.plusDays(1))) {
                var start = date.atStartOfDay(zone).toInstant();
                var end = date.plusDays(1).atStartOfDay(zone).toInstant();
                var prices = preview.prices().stream().filter(price -> price.start().isBefore(end) && price.end().isAfter(start)).toList();
                var periods = selected.periods().stream().filter(period -> period.start().isBefore(end) && period.end().isAfter(start)).toList();
                graph.add(new Paragraph(date.toString()), new HeatPumpControlPreviewChart(prices, periods.stream().map(period ->
                        new HeatPumpControlPreviewChart.PlotPeriod(period.start().toEpochMilli(), period.end().toEpochMilli(),
                                period.state() == null ? null : period.state().power(),
                                period.state() == null ? null : period.state().temperature(),
                                period.state() == null ? null : period.state().mode(), period.assumed(), summary(period), reason(period)))
                        .toList(), date, zone, preview.generatedAt(), i18n));
                if (prices.stream().anyMatch(price -> price.price() == null))
                    graph.add(new Paragraph(i18n.t("controlTable.heatPumpPreview.missingPrices")));
            }
        } catch (IllegalArgumentException ex) { graph.add(new Paragraph(t("invalid"))); }
        catch (Exception ex) { graph.add(new Paragraph(t("previewError"))); }
    }

    private String reason(Period period) {
        if (period.reason() == Reason.INACTIVE_TIME) return t("outside");
        if (period.reason() == Reason.PRICE) return (period.comparison() == ComparisonType.LESS_THAN ? "< " : "> ") + period.threshold() + " snt/kWh";
        if (period.reason() == Reason.SCHEDULE) return t("schedule");
        return i18n.t("controlTable.heatPumpPreview.reason." + period.reason().name());
    }

    private String summary(Period period) {
        if (period.state() == null) return i18n.t("controlTable.heatPumpPreview.unknown");
        var state = period.state();
        return (state.power() == null ? "?" : i18n.t("controlTable.heatPumpPreview." + (state.power() ? "on" : "off")))
                + (Boolean.FALSE.equals(state.power()) || state.temperature() == null ? "" : " · " + state.temperature() + " °C");
    }

    private String t(String key, Object... args) { return i18n.t("controlTable.heatPumpRules." + key, args); }

    private class Row {
        final Long id;
        final boolean inactive;
        final VerticalLayout layout = new VerticalLayout();
        final ComboBox<String> condition = new ComboBox<>(t("when"));
        final NumberField price = new NumberField(t("price"));
        final ComboBox<ControlAction> action = new ComboBox<>(t("action"));
        final TextField state = new TextField(t("state"));
        final Paragraph captured = new Paragraph();
        final NumberField power = new NumberField(i18n.t("controlTable.field.estimatedPowerKw"));

        Row(ControlHeatPumpResponse row, boolean inactive) {
            this.id = row == null ? null : row.getId();
            this.inactive = inactive;
            condition.setItems("LESS_THAN", "GREATER_THAN", "SCHEDULE");
            condition.setItemLabelGenerator(key -> key.equals("SCHEDULE") ? t("schedule") : t(key.equals("LESS_THAN") ? "below" : "above"));
            condition.setValue(row != null ? row.getComparisonType() == null ? "SCHEDULE" : row.getComparisonType().name() : "LESS_THAN");
            price.setStep(0.1);
            price.setValue(row == null || row.getPriceLimit() == null ? null : row.getPriceLimit().doubleValue());
            price.setVisible(!inactive && !condition.getValue().equals("SCHEDULE"));
            action.setItems(ControlAction.values());
            action.setItemLabelGenerator(value -> i18n.t("controlAction." + value.name()));
            action.setValue(row == null ? inactive ? ControlAction.TURN_OFF : ControlAction.TURN_ON : row.getControlAction());
            state.setValue(row == null || row.getStateHex() == null ? "" : row.getStateHex());
            state.setReadOnly(true);
            power.setMin(0);
            power.setValue(row == null || row.getEstimatedPowerKw() == null ? null : row.getEstimatedPowerKw().doubleValue());
            Button capture = new Button(t("capture"), event -> {
                if (pump.getValue() != null) states.openStateDialog(pump.getValue(), state);
            });
            layout.setPadding(false);
            layout.setWidthFull();
            if (!inactive) layout.add(condition, price);
            captured.setText(t(state.isEmpty() ? "notCaptured" : "captured"));
            captured.getStyle().set("font-size", "var(--lumo-font-size-s)").set("margin", "0");
            layout.add(action, capture, captured, new Details(t("state"), state, power));
            layout.getChildren().forEach(child -> child.getElement().getStyle().set("width", "100%"));
            condition.addValueChangeListener(event -> { price.setVisible(!event.getValue().equals("SCHEDULE")); changed(); });
            price.addValueChangeListener(event -> changed());
            action.addValueChangeListener(event -> changed());
            state.addValueChangeListener(event -> {
                captured.setText(t(state.isEmpty() ? "notCaptured" : "captured"));
                if (!state.isEmpty()) pump.setReadOnly(true);
                changed();
            });
            power.addValueChangeListener(event -> changed());
            DesktopFormState.watchChanges(price, HeatPumpRuleSetDialog.this);
            DesktopFormState.watchChanges(power, HeatPumpRuleSetDialog.this);
        }

        Rule value() {
            boolean priced = !inactive && !"SCHEDULE".equals(condition.getValue());
            return new Rule(id, state.getValue(), action.getValue(), priced ? ComparisonType.valueOf(condition.getValue()) : null,
                    priced && price.getValue() != null ? BigDecimal.valueOf(price.getValue()) : null,
                    power.getValue() == null ? null : BigDecimal.valueOf(power.getValue()), inactive);
        }
    }
}
