/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import com.nitramite.porssiohjain.entity.enums.WaterMeterNotificationFrequency;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DemoAccountMutationException;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.WaterMeterService;
import com.nitramite.porssiohjain.services.watermeters.WaterMeterException;
import com.nitramite.porssiohjain.views.components.DeviceMeasurementHistoryChart;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

@Route(value = "water-meters", layout = MainLayout.class)
@PageTitle("Pörssiohjain - Water meters")
@PermitAll
public class WaterMetersView extends VerticalLayout implements BeforeEnterObserver {
    private final AuthService auth;
    private final I18nService i18n;
    private final WaterMeterService meters;
    private final Grid<WaterMeterService.Meter> grid = new Grid<>(WaterMeterService.Meter.class, false);
    private final TextField name = new TextField();
    private final ComboBox<WaterMeterProvider> provider = new ComboBox<>();
    private final TextField portal = new TextField();
    private final TextField payer = new TextField();
    private final PasswordField meterNumber = new PasswordField();
    private final Checkbox enabled = new Checkbox();
    private final Checkbox pushEnabled = new Checkbox();
    private final ComboBox<WaterMeterNotificationFrequency> pushFrequency = new ComboBox<>();
    private final ComboBox<DayOfWeek> pushWeekday = new ComboBox<>();
    private final TimePicker pushTime = new TimePicker();
    private final ComboBox<String> pushTimezone = new ComboBox<>();
    private final DesktopCreateDialog editor;
    private Long editingId;

    public WaterMetersView(AuthService auth, I18nService i18n, WaterMeterService meters) {
        this.auth = auth;
        this.i18n = i18n;
        this.meters = meters;
        VaadinSession session = VaadinSession.getCurrent();
        Locale locale = session == null ? null : session.getAttribute(Locale.class);
        if (locale != null) UI.getCurrent().setLocale(locale);

        setWidthFull();
        setAlignItems(Alignment.STRETCH);
        add(new H2(t("home.waterMeters")), new Paragraph(t("waterMeters.description")),
                new Paragraph(t("waterMeters.snapshotHint")));

        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.addColumn(WaterMeterService.Meter::name).setHeader(t("waterMeters.name")).setAutoWidth(true);
        grid.addColumn(meter -> providerLabel(meter.provider())).setHeader(t("waterMeters.provider")).setAutoWidth(true);
        grid.addColumn(this::value).setHeader(t("waterMeters.latest")).setAutoWidth(true);
        grid.addColumn(meter -> format(meter.observedAt())).setHeader(t("waterMeters.observedAt")).setAutoWidth(true);
        grid.addColumn(this::status).setHeader(t("waterMeters.status")).setAutoWidth(true);
        grid.addColumn(meter -> format(meter.lastPollAttemptAt())).setHeader(t("waterMeters.lastAttempt"))
                .setAutoWidth(true);
        grid.addColumn(this::pushSchedule).setHeader(t("waterMeters.push.title")).setAutoWidth(true);
        grid.addComponentColumn(this::actions).setHeader(t("waterMeters.actions")).setAutoWidth(true).setFlexGrow(0);
        add(grid);

        name.setLabel(t("waterMeters.name"));
        name.setMaxLength(255);
        name.setRequiredIndicatorVisible(true);
        provider.setLabel(t("waterMeters.provider"));
        provider.setItems(WaterMeterProvider.values());
        provider.setItemLabelGenerator(this::providerLabel);
        provider.setValue(WaterMeterProvider.WRM_SYSTEMS_WMD);
        provider.setRequiredIndicatorVisible(true);
        portal.setLabel(t("waterMeters.portal"));
        portal.setPlaceholder(t("waterMeters.portalPlaceholder"));
        portal.setMaxLength(255);
        portal.setRequiredIndicatorVisible(true);
        payer.setLabel(t("waterMeters.payerNumber"));
        payer.setMaxLength(30);
        payer.setRequiredIndicatorVisible(true);
        payer.setAutocomplete(Autocomplete.OFF);
        meterNumber.setLabel(t("waterMeters.meterNumber"));
        meterNumber.setMaxLength(30);
        meterNumber.setRequiredIndicatorVisible(true);
        meterNumber.setAutocomplete(Autocomplete.OFF);
        enabled.setLabel(t("waterMeters.enabled"));
        enabled.setValue(true);
        pushEnabled.setLabel(t("waterMeters.push.enabled"));
        pushFrequency.setLabel(t("waterMeters.push.frequency"));
        pushFrequency.setItems(WaterMeterNotificationFrequency.values());
        pushFrequency.setItemLabelGenerator(value -> t("waterMeters.push." + value.name().toLowerCase(Locale.ROOT)));
        pushFrequency.setValue(WaterMeterNotificationFrequency.DAILY);
        pushWeekday.setLabel(t("waterMeters.push.weekday"));
        pushWeekday.setItems(DayOfWeek.values());
        pushWeekday.setItemLabelGenerator(value -> value.getDisplayName(TextStyle.FULL, UI.getCurrent().getLocale()));
        pushWeekday.setValue(DayOfWeek.MONDAY);
        pushTime.setLabel(t("waterMeters.push.time"));
        pushTime.setStep(Duration.ofMinutes(1));
        pushTime.setValue(LocalTime.of(12, 5));
        pushTimezone.setLabel(t("waterMeters.push.timezone"));
        pushTimezone.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        pushTimezone.setValue("Europe/Helsinki");
        pushEnabled.addValueChangeListener(event -> updatePushFields());
        pushFrequency.addValueChangeListener(event -> updatePushFields());
        updatePushFields();
        FormLayout form = new FormLayout(name, provider, portal, payer, meterNumber, enabled,
                pushEnabled, pushFrequency, pushWeekday, pushTime, pushTimezone);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2));
        form.setColspan(portal, 2);
        DesktopFormState.watch(form);
        editor = new DesktopCreateDialog(this, t("waterMeters.add"), i18n);
        editor.add(form, new Paragraph(t("waterMeters.credentialsHint")), new Paragraph(t("waterMeters.push.hint")));
        editor.getFooter().add(new Button(t("common.save"), event -> save()));
        editor.openButton().setEnabled(writable());
        editor.onCreate(() -> {
            if (editingId == null) {
                editor.setHeaderTitle(t("waterMeters.add"));
                editor.open();
            }
            else editor.openPrepared(t("waterMeters.add"), () -> prepare(null));
        });
        add(editor.openButton());
        refresh();
    }

    private FlexLayout actions(WaterMeterService.Meter meter) {
        Button edit = new Button(t("waterMeters.edit"), VaadinIcon.EDIT.create(), event ->
                editor.openPrepared(t("waterMeters.edit"), () -> prepare(meter)));
        Button read = new Button(t("waterMeters.readNow"), VaadinIcon.REFRESH.create(), event -> poll(meter));
        Button graph = new Button(t("waterMeters.graph"), VaadinIcon.LINE_CHART.create(), event -> openHistory(meter));
        Button delete = new Button(t("common.delete"), VaadinIcon.TRASH.create(), event -> confirmDelete(meter));
        edit.setEnabled(writable());
        delete.setEnabled(writable());
        read.setEnabled(writable() && meter.enabled());
        FlexLayout actions = new FlexLayout(edit, read, graph, delete);
        actions.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        actions.getStyle().set("gap", "var(--lumo-space-xs)");
        return actions;
    }

    private void prepare(WaterMeterService.Meter meter) {
        editingId = meter == null ? null : meter.id();
        name.setValue(meter == null ? "" : meter.name());
        provider.setValue(meter == null ? WaterMeterProvider.WRM_SYSTEMS_WMD : meter.provider());
        portal.setValue(meter == null ? "" : meter.portalUrl());
        payer.clear();
        meterNumber.clear();
        payer.setRequiredIndicatorVisible(meter == null);
        meterNumber.setRequiredIndicatorVisible(meter == null);
        payer.setPlaceholder(meter == null ? "" : t("waterMeters.keepCredential"));
        meterNumber.setPlaceholder(meter == null ? "" : t("waterMeters.keepCredential"));
        enabled.setValue(meter == null || meter.enabled());
        var notification = meter == null ? WaterMeterService.NotificationSettings.defaults() : meter.notifications();
        pushFrequency.setValue(notification.frequency());
        pushWeekday.setValue(notification.weekday());
        pushTime.setValue(notification.time());
        pushTimezone.setValue(notification.timezone());
        pushEnabled.setValue(notification.enabled());
        updatePushFields();
    }

    private void save() {
        AccountEntity account = account();
        if (account == null || !writable()) return;
        try {
            meters.save(account.getId(), editingId, new WaterMeterService.Settings(name.getValue(), provider.getValue(),
                    portal.getValue(), payer.getValue(), meterNumber.getValue(), enabled.getValue(),
                    new WaterMeterService.NotificationSettings(pushEnabled.getValue(), pushFrequency.getValue(),
                            pushTime.getValue(), pushWeekday.getValue(), pushTimezone.getValue())));
            prepare(null);
            editor.savedAndClose();
            refresh();
            Notification.show(t("waterMeters.saved"));
        } catch (WaterMeterException exception) {
            showError(exception.getMessage());
        } catch (DemoAccountMutationException exception) {
            showError("waterMeters.error.readOnly");
        }
    }

    private void poll(WaterMeterService.Meter meter) {
        AccountEntity account = account();
        if (account == null || !writable()) return;
        try {
            var result = meters.pollNow(account.getId(), meter.id());
            refresh();
            if (result.lastErrorKey() == null) Notification.show(t("waterMeters.readSuccess", value(result)));
            else showError(result.lastErrorKey());
        } catch (WaterMeterException exception) {
            showError(exception.getMessage());
        } catch (DemoAccountMutationException exception) {
            showError("waterMeters.error.readOnly");
        }
    }

    private void confirmDelete(WaterMeterService.Meter meter) {
        ConfirmDialog confirmation = new ConfirmDialog();
        confirmation.setHeader(t("common.delete"));
        confirmation.setText(t("waterMeters.deleteConfirm", meter.name()));
        confirmation.setCancelable(true);
        confirmation.setCancelText(t("common.cancel"));
        confirmation.setConfirmText(t("common.delete"));
        confirmation.setConfirmButtonTheme("error primary");
        confirmation.addConfirmListener(event -> {
            AccountEntity account = account();
            if (account == null || !writable()) return;
            try {
                meters.delete(account.getId(), meter.id());
                if (meter.id().equals(editingId)) {
                    prepare(null);
                    editor.savedAndClose();
                }
                refresh();
            } catch (WaterMeterException exception) {
                showError(exception.getMessage());
            } catch (DemoAccountMutationException exception) {
                showError("waterMeters.error.readOnly");
            }
        });
        add(confirmation);
        confirmation.addOpenedChangeListener(event -> {
            if (!event.isOpened()) remove(confirmation);
        });
        confirmation.open();
    }

    private void updatePushFields() {
        pushFrequency.setEnabled(pushEnabled.getValue());
        pushTime.setEnabled(pushEnabled.getValue());
        pushTimezone.setEnabled(pushEnabled.getValue());
        pushWeekday.setVisible(pushFrequency.getValue() == WaterMeterNotificationFrequency.WEEKLY);
        pushWeekday.setEnabled(pushEnabled.getValue());
    }

    private String pushSchedule(WaterMeterService.Meter meter) {
        var settings = meter.notifications();
        if (!settings.enabled()) return t("waterMeters.push.disabled");
        String time = settings.time().format(DateTimeFormatter.ofPattern("HH:mm"));
        if (settings.frequency() == WaterMeterNotificationFrequency.DAILY)
            return t("waterMeters.push.dailySchedule", time, settings.timezone());
        return t("waterMeters.push.weeklySchedule", settings.weekday().getDisplayName(TextStyle.FULL,
                UI.getCurrent().getLocale()), time, settings.timezone());
    }

    private String providerLabel(WaterMeterProvider value) {
        return switch (value) {
            case WRM_SYSTEMS_WMD -> "WRM Systems WMD";
        };
    }

    private void openHistory(WaterMeterService.Meter selected) {
        AccountEntity account = account();
        if (account == null) return;
        try {
            var history = meters.history(account.getId(), selected.id());
            var points = history.stream().map(row -> new DeviceMeasurementHistoryChart.HistoryPoint(
                    row.observedAt(), row.totalCubicMeters().doubleValue())).toList();
            var chart = DeviceMeasurementHistoryChart.history(points, ZoneId.of("Europe/Helsinki"),
                    t("waterMeters.latest"), "m³", t("waterMeters.observedAt"), t("waterMeters.noHistory"),
                    UI.getCurrent().getLocale().toLanguageTag());
            Grid<WaterMeterService.Measurement> table = new Grid<>(WaterMeterService.Measurement.class, false);
            table.addColumn(row -> format(row.observedAt())).setHeader(t("waterMeters.observedAt")).setAutoWidth(true);
            table.addColumn(row -> formatCubicMeters(row.totalCubicMeters())).setHeader(t("waterMeters.latest")).setAutoWidth(true);
            table.setItems(history);
            table.setWidthFull();
            table.setHeight("260px");
            table.getStyle().set("flex-shrink", "0");
            VerticalLayout content = new VerticalLayout(new Paragraph(t("waterMeters.snapshotHint")), chart, table);
            content.setPadding(false);
            content.setWidthFull();
            content.setAlignItems(Alignment.STRETCH);
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle(selected.name() + " · " + t("waterMeters.graph"));
            dialog.setWidth("min(1000px, 95vw)");
            dialog.setMaxHeight("90vh");
            dialog.add(content);
            dialog.getFooter().add(new Button(t("common.close"), event -> dialog.close()));
            add(dialog);
            dialog.addOpenedChangeListener(event -> {
                if (!event.isOpened()) remove(dialog);
            });
            dialog.open();
        } catch (WaterMeterException exception) {
            showError(exception.getMessage());
        }
    }

    private void refresh() {
        AccountEntity account = account();
        grid.setItems(account == null ? java.util.List.of() : meters.list(account.getId()));
    }

    private String value(WaterMeterService.Meter meter) {
        if (meter.latestTotalCubicMeters() == null) return "—";
        return formatCubicMeters(meter.latestTotalCubicMeters());
    }

    private String formatCubicMeters(java.math.BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(UI.getCurrent().getLocale());
        format.setMaximumFractionDigits(6);
        return format.format(value) + " m³";
    }

    private String status(WaterMeterService.Meter meter) {
        if (!meter.enabled()) return t("waterMeters.disabled");
        if (meter.lastErrorKey() != null) return t(meter.lastErrorKey());
        return t(meter.observedAt() == null ? "waterMeters.waiting" : "waterMeters.ok");
    }

    private String format(Instant instant) {
        return instant == null ? "—" : DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(ZoneId.of("Europe/Helsinki")).format(instant);
    }

    private AccountEntity account() {
        return ViewAuthUtils.findAuthenticatedAccount(auth);
    }

    private boolean writable() {
        AccountEntity account = account();
        return account != null && !account.isDemo();
    }

    private void showError(String key) {
        Notification.show(t(key), 5000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, auth);
    }
}
