/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerConfigurationService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerConfigurationService.ComfortScheduleConfiguration;
import com.nitramite.porssiohjain.services.models.HeatingComfortSchedule;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Named daily schedules shared by rooms in the selected site. */
final class HeatingComfortSchedulesDialog extends Dialog {
    private final HeatingPlannerConfigurationService service;
    private final I18nService i18n;
    private final Long accountId;
    private final Long siteId;
    private final Runnable onSaved;
    private final Predicate<Long> usedByDraft;
    private final VerticalLayout list = new VerticalLayout();

    HeatingComfortSchedulesDialog(HeatingPlannerConfigurationService service, I18nService i18n,
                                   Long accountId, Long siteId, Runnable onSaved, Predicate<Long> usedByDraft) {
        this.service = service;
        this.i18n = i18n;
        this.accountId = accountId;
        this.siteId = siteId;
        this.onSaved = onSaved;
        this.usedByDraft = usedByDraft;
        setHeaderTitle(i18n.t("heatingPlanner.comfort.schedules"));
        setWidth("760px");
        setMaxWidth("95vw");
        list.setPadding(false);
        add(new Paragraph(i18n.t("heatingPlanner.comfort.help")), list);
        getFooter().add(new Button(i18n.t("heatingPlanner.comfort.addSchedule"), event -> edit(null)),
                new Button(i18n.t("heatingPlanner.comfort.close"), event -> close()));
        refresh();
    }

    private void refresh() {
        list.removeAll();
        for (var schedule : service.comfortSchedules(accountId, siteId)) {
            var label = new Span(schedule.name() + " · " + i18n.t("heatingPlanner.comfort.default") + " "
                    + schedule.schedule().defaultTarget() + " °C");
            var edit = new Button(i18n.t("heatingPlanner.comfort.edit"), event -> edit(schedule));
            var delete = new Button(i18n.t("heatingPlanner.comfort.delete"), event -> {
                try {
                    if (usedByDraft.test(schedule.id())) {
                        throw new IllegalArgumentException("heatingPlanner.comfort.inUse");
                    }
                    service.deleteComfortSchedule(accountId, siteId, schedule.id());
                    onSaved.run();
                    refresh();
                } catch (IllegalArgumentException ex) {
                    error(ex);
                }
            });
            var row = new HorizontalLayout(label, edit, delete);
            row.setWidthFull();
            row.setWrap(true);
            row.setAlignItems(VerticalLayout.Alignment.CENTER);
            list.add(row);
        }
    }

    private void edit(ComfortScheduleConfiguration existing) {
        Dialog editor = new Dialog();
        editor.setHeaderTitle(i18n.t("heatingPlanner.comfort.schedules"));
        editor.setWidth("700px");
        editor.setMaxWidth("95vw");
        editor.setCloseOnEsc(false);
        editor.setCloseOnOutsideClick(false);
        TextField name = new TextField(i18n.t("heatingPlanner.comfort.name"));
        name.setMaxLength(128);
        name.setValue(existing == null ? "" : existing.name());
        NumberField defaultTarget = temperature(i18n.t("heatingPlanner.comfort.default"),
                existing == null ? new BigDecimal("21") : existing.schedule().defaultTarget());
        FormLayout base = new FormLayout(name, defaultTarget);
        base.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("450px", 2));
        VerticalLayout windows = new VerticalLayout();
        windows.setPadding(false);
        List<PeriodFields> periods = new ArrayList<>();
        java.util.function.Consumer<HeatingComfortSchedule.Period> addPeriod = period -> {
            TimePicker from = time("heatingPlanner.comfort.from", period.from());
            TimePicker to = time("heatingPlanner.comfort.to", period.to());
            NumberField target = temperature(i18n.t("heatingPlanner.comfort.target"), period.target());
            FormLayout row = new FormLayout();
            PeriodFields fields = new PeriodFields(from, to, target);
            periods.add(fields);
            Button remove = new Button(VaadinIcon.TRASH.create(), event -> {
                periods.remove(fields);
                windows.remove(row);
                DesktopFormState.markDirty(editor);
            });
            remove.setAriaLabel(i18n.t("heatingPlanner.comfort.delete"));
            row.add(from, to, target, remove);
            row.setWidthFull();
            row.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 4));
            windows.add(row);
            DesktopFormState.watch(row);
        };
        if (existing != null) existing.schedule().periods().forEach(addPeriod);
        Button addWindow = new Button(i18n.t("heatingPlanner.comfort.addWindow"), event -> {
            addPeriod.accept(new HeatingComfortSchedule.Period(LocalTime.of(6, 0), LocalTime.of(9, 0),
                    new BigDecimal("22")));
            DesktopFormState.markDirty(editor);
        });
        editor.add(base, new Paragraph(i18n.t("heatingPlanner.comfort.windowHelp")), windows, addWindow);
        Button save = new Button(i18n.t("heatingPlanner.comfort.save"), event -> {
            try {
                List<HeatingComfortSchedule.Period> values = periods.stream().map(fields ->
                        new HeatingComfortSchedule.Period(fields.from().getValue(), fields.to().getValue(),
                                value(fields.target()))).toList();
                service.saveComfortSchedule(accountId, siteId, new ComfortScheduleConfiguration(
                        existing == null ? null : existing.id(), name.getValue(),
                        new HeatingComfortSchedule(value(defaultTarget), values)));
                DesktopFormState.saved(editor);
                onSaved.run();
                refresh();
                editor.close();
                Notification.show(i18n.t("heatingPlanner.comfort.saved"));
            } catch (IllegalArgumentException ex) {
                error(ex);
            }
        });
        editor.getFooter().add(save, new Button(i18n.t("heatingPlanner.comfort.cancel"), event -> {
            DesktopFormState.saved(editor);
            editor.close();
        }));
        DesktopFormState.watch(base);
        add(editor);
        editor.addOpenedChangeListener(event -> { if (!event.isOpened()) editor.removeFromParent(); });
        editor.open();
    }

    private NumberField temperature(String label, BigDecimal value) {
        NumberField field = new NumberField(label);
        field.setMin(5);
        field.setMax(35);
        field.setStep(0.25);
        field.setValue(value.doubleValue());
        field.setSuffixComponent(new Span("°C"));
        field.setWidthFull();
        return field;
    }

    private TimePicker time(String key, LocalTime value) {
        TimePicker field = new TimePicker(i18n.t(key), value);
        field.setStep(Duration.ofMinutes(1));
        field.setWidthFull();
        return field;
    }

    private BigDecimal value(NumberField field) {
        Double value = field.getValue();
        if (field.isInvalid() || value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException("heatingPlanner.comfort.invalidTemperature");
        }
        return BigDecimal.valueOf(value);
    }

    private void error(IllegalArgumentException ex) {
        Notification.show(ex.getMessage().startsWith("heatingPlanner.") ? i18n.t(ex.getMessage()) : ex.getMessage())
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private record PeriodFields(TimePicker from, TimePicker to, NumberField target) { }
}
