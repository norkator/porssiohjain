/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.DeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceMeasurementService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.PublicMeasurementService;
import com.nitramite.porssiohjain.services.DemoAccountMutationException;
import com.nitramite.porssiohjain.views.components.DeviceMeasurementHistoryChart;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Route(value = "measurements", layout = MainLayout.class)
@PageTitle("Pörssiohjain - Measurements")
@PermitAll
public class MeasurementsView extends VerticalLayout implements BeforeEnterObserver {
    private final AuthService authService;
    private final I18nService i18n;
    private final DeviceMeasurementService measurements;
    private final PublicMeasurementService publicMeasurements;
    private final TextField search = new TextField();
    private final ComboBox<String> timezone = new ComboBox<>();
    private final Span status = new Span();
    private final VerticalLayout groups = new VerticalLayout();
    private final VerticalLayout sharingPanel = new VerticalLayout();
    private PublicMeasurementService.OwnerSettings sharing = new PublicMeasurementService.OwnerSettings(null, List.of());
    private List<DeviceMeasurementEntity> latest = List.of();

    public MeasurementsView(AuthService authService, I18nService i18n, DeviceMeasurementService measurements,
                            PublicMeasurementService publicMeasurements) {
        this.authService = authService;
        this.i18n = i18n;
        this.measurements = measurements;
        this.publicMeasurements = publicMeasurements;
        VaadinSession session = VaadinSession.getCurrent();
        Locale locale = session == null ? null : session.getAttribute(Locale.class);
        if (locale != null) UI.getCurrent().setLocale(locale);

        setWidthFull();
        setAlignItems(Alignment.STRETCH);
        addClassName("measurements-view");
        add(new H2(t("home.measurements")), new Paragraph(t("measurements.description")));

        search.setLabel(t("measurements.search"));
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setWidth("min(360px, 100%)");
        timezone.setLabel(t("measurements.timezone"));
        timezone.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        timezone.setValue("Europe/Helsinki");
        timezone.setWidth("min(260px, 100%)");
        Button refresh = new Button(t("measurements.refresh"), VaadinIcon.REFRESH.create(), event -> refresh());
        FlexLayout toolbar = new FlexLayout(search, timezone, refresh);
        toolbar.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        toolbar.setAlignItems(Alignment.END);
        toolbar.getStyle().set("gap", "var(--lumo-space-m)");
        groups.setPadding(false);
        groups.setAlignItems(Alignment.STRETCH);
        sharingPanel.setPadding(false);
        sharingPanel.setAlignItems(Alignment.STRETCH);
        search.addValueChangeListener(event -> renderGroups());
        timezone.addValueChangeListener(event -> {
            if (event.getValue() == null) timezone.setValue(event.getOldValue());
            else renderGroups();
        });
        add(toolbar, sharingPanel, status, groups);
        refresh();
    }

    private void refresh() {
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        // Include every retained series, even if its sensor has been silent for more than 12 hours.
        latest = account == null ? List.of() : measurements.latestForAccount(account.getId(), Instant.EPOCH);
        sharing = account == null ? new PublicMeasurementService.OwnerSettings(null, List.of())
                : publicMeasurements.ownerSettings(account.getId());
        renderSharing();
        renderGroups();
    }

    private void renderSharing() {
        sharingPanel.removeAll();
        sharingPanel.add(new H3(t("measurements.sharing.title")), new Paragraph(t("measurements.sharing.description")));
        if (sharing.pageId() != null) {
            Anchor link = new Anchor(PublicMeasurementsView.path(sharing.pageId()), t("measurements.sharing.open"));
            link.setTarget("_blank");
            link.getElement().setAttribute("rel", "noopener noreferrer");
            Button copy = new Button(t("measurements.sharing.copy"), VaadinIcon.COPY.create(), event ->
                    link.getElement().executeJs("navigator.clipboard.writeText(this.href)"));
            FlexLayout links = new FlexLayout(link, copy);
            links.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            links.setAlignItems(Alignment.CENTER);
            links.getStyle().set("gap", "var(--lumo-space-m)");
            sharingPanel.add(links);
        }
        if (sharing.selections().isEmpty()) sharingPanel.add(new Paragraph(t("measurements.sharing.empty")));
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        boolean writable = account != null && !account.isDemo() && !ViewAuthUtils.isImpersonating();
        for (var selected : sharing.selections()) {
            Button remove = new Button(t("measurements.sharing.remove"), event -> changePublication(
                    selected.deviceId(), selected.type(), selected.key(), false));
            remove.setEnabled(writable);
            FlexLayout item = new FlexLayout(new Span(selected.deviceName() + " · " + typeLabel(selected.type())
                    + " · " + selected.key()), remove);
            item.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            item.setAlignItems(Alignment.CENTER);
            item.getStyle().set("gap", "var(--lumo-space-m)");
            sharingPanel.add(item);
        }
    }

    private void changePublication(Long deviceId, DeviceMeasurementType type, String key, boolean published) {
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null || account.isDemo() || ViewAuthUtils.isImpersonating()) return;
        try {
            publicMeasurements.setPublished(account.getId(), deviceId, type, key, published);
        } catch (IllegalArgumentException | DemoAccountMutationException exception) {
            Notification.show(t("measurements.sharing.failed"), 5000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
        sharing = publicMeasurements.ownerSettings(account.getId());
        renderSharing();
        renderGroups();
    }

    private void renderGroups() {
        groups.removeAll();
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        List<DeviceMeasurementEntity> visible = latest.stream()
                .filter(row -> (deviceName(row) + " " + row.getSource() + " " + row.getSourceKey()
                        + " " + row.getMeasurementKey() + " " + typeLabel(row.getMeasurementType()))
                        .toLowerCase(Locale.ROOT).contains(query))
                .toList();
        status.setText(t("measurements.summary", visible.size()));
        if (visible.isEmpty()) {
            groups.add(new Paragraph(t(latest.isEmpty() ? "measurements.empty" : "measurements.noMatches")));
            return;
        }
        for (DeviceMeasurementType type : DeviceMeasurementType.values()) {
            List<DeviceMeasurementEntity> rows = visible.stream()
                    .filter(row -> row.getMeasurementType() == type).toList();
            if (!rows.isEmpty()) {
                groups.add(new H3(typeLabel(type) + " (" + rows.size() + ")"), readingsGrid(rows));
            }
        }
    }

    private Grid<DeviceMeasurementEntity> readingsGrid(List<DeviceMeasurementEntity> rows) {
        Grid<DeviceMeasurementEntity> grid = new Grid<>(DeviceMeasurementEntity.class, false);
        grid.setWidthFull();
        grid.addColumn(this::deviceName).setHeader(t("measurements.device")).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(this::value).setHeader(t("measurements.value")).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(row -> format(row.getObservedAt())).setHeader(t("measurements.observed"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(this::measuredAt).setHeader(t("measurements.measured")).setAutoWidth(true);
        grid.addColumn(DeviceMeasurementEntity::getSource).setHeader(t("measurements.source")).setAutoWidth(true);
        grid.addColumn(row -> row.getMeasurementKey() + " · " + row.getSourceKey())
                .setHeader(t("measurements.property")).setAutoWidth(true);
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        var publishColumn = grid.addComponentColumn(row -> {
            Checkbox checkbox = new Checkbox(t("measurements.sharing.public"));
            checkbox.setAriaLabel(t("measurements.sharing.public") + " · " + deviceName(row)
                    + " · " + typeLabel(row.getMeasurementType()) + " · " + row.getMeasurementKey());
            checkbox.setValue(sharing.selections().stream().anyMatch(selected ->
                    selected.deviceId().equals(row.getDevice().getId()) && selected.type() == row.getMeasurementType()
                            && selected.key().equals(row.getMeasurementKey())));
            checkbox.setEnabled(account != null && !account.isDemo() && !ViewAuthUtils.isImpersonating());
            checkbox.addValueChangeListener(event -> changePublication(row.getDevice().getId(), row.getMeasurementType(),
                    row.getMeasurementKey(), event.getValue()));
            return checkbox;
        }).setKey("public").setAutoWidth(true).setFlexGrow(0);
        var plotColumn = grid.addComponentColumn(row -> {
            Button plot = new Button(t("measurements.plot"), VaadinIcon.LINE_CHART.create(), event -> openHistory(row));
            plot.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            return plot;
        }).setAutoWidth(true).setFlexGrow(0);
        grid.addItemClickListener(event -> {
            if (event.getColumn() != plotColumn && event.getColumn() != publishColumn) openHistory(event.getItem());
        });
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setAllRowsVisible(true);
        grid.setItems(rows);
        return grid;
    }

    private void openHistory(DeviceMeasurementEntity selected) {
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null) return;
        List<DeviceMeasurementEntity> history = measurements.recentHistory(account.getId(), selected.getDevice().getId(),
                selected.getMeasurementType(), selected.getMeasurementKey());
        Grid<DeviceMeasurementEntity> table = new Grid<>(DeviceMeasurementEntity.class, false);
        table.addColumn(row -> format(row.getObservedAt())).setHeader(t("measurements.observed")).setAutoWidth(true);
        table.addColumn(this::measuredAt).setHeader(t("measurements.measured")).setAutoWidth(true);
        table.addColumn(row -> format(row.getReceivedAt())).setHeader(t("measurements.received")).setAutoWidth(true);
        table.addColumn(this::value).setHeader(t("measurements.value")).setAutoWidth(true);
        table.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        table.setItems(history);
        table.setWidthFull();
        table.setHeight("320px");
        table.getStyle().set("flex-shrink", "0");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setWidthFull();
        content.getStyle().set("min-width", "0");
        content.setAlignItems(Alignment.STRETCH);
        DeviceMeasurementHistoryChart chart = new DeviceMeasurementHistoryChart(history, zone(),
                typeLabel(selected.getMeasurementType()), selected.getUnit(), t("measurements.chartTime"),
                t("measurements.noHistory"), UI.getCurrent().getLocale().toLanguageTag());
        content.add(new Paragraph(t("measurements.historySummary", history.size(), timezone.getValue())),
                new Paragraph(t("measurements.snapshotHint")),
                chart, table);
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(deviceName(selected) + " · " + typeLabel(selected.getMeasurementType())
                + " · " + selected.getMeasurementKey());
        dialog.setWidth("min(1000px, 95vw)");
        dialog.setMaxHeight("90vh");
        dialog.add(content);
        dialog.getFooter().add(new Button(t("common.close"), event -> dialog.close()));
        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) remove(dialog);
        });
        add(dialog);
        dialog.open();
    }

    private String deviceName(DeviceMeasurementEntity row) {
        String name = row.getDevice().getDeviceName();
        return name == null || name.isBlank() ? t("measurements.unnamedDevice", row.getDevice().getId()) : name;
    }

    private String measuredAt(DeviceMeasurementEntity row) {
        return row.getMeasuredAt() == null ? t("measurements.cloudSnapshot") : format(row.getMeasuredAt());
    }

    private String value(DeviceMeasurementEntity row) {
        return row.getValue().stripTrailingZeros().toPlainString() + " " + row.getUnit();
    }

    private String format(Instant at) {
        return at == null ? "–" : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone()).format(at);
    }

    private ZoneId zone() {
        return ZoneId.of(timezone.getValue());
    }

    private String typeLabel(DeviceMeasurementType type) {
        return t("measurements.type." + type.name());
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, authService);
    }
}
