/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.*;
import com.nitramite.porssiohjain.views.components.Divider;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@JsModule("./js/apexcharts.min.js")
@PageTitle("Pörssiohjain - Production Source")
@Route("production-source/:sourceId")
@PermitAll
public class ProductionSourceView extends VerticalLayout implements BeforeEnterObserver {

    private final I18nService i18n;
    private final AuthService authService;
    private final ProductionSourceService productionSourceService;
    private final DeviceService deviceService;
    private final SiteService siteService;
    private final HeatPumpStateDialogService heatPumpStateDialogService;

    private Long sourceId;
    private Long accountId;

    private final Grid<ProductionSourceDeviceResponse> deviceGrid = new Grid<>(ProductionSourceDeviceResponse.class, false);
    private final Grid<ProductionSourceHeatPumpResponse> heatPumpGrid = new Grid<>(ProductionSourceHeatPumpResponse.class, false);

    private Div currentKwValue;
    private Div peakKwValue;
    private Div chartDiv;

    private ScheduledExecutorService scheduler;
    private UI ui;

    @Autowired
    public ProductionSourceView(
            AuthService authService,
            I18nService i18n,
            ProductionSourceService productionSourceService,
            DeviceService deviceService,
            SiteService siteService,
            HeatPumpStateDialogService heatPumpStateDialogService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.productionSourceService = productionSourceService;
        this.deviceService = deviceService;
        this.siteService = siteService;
        this.heatPumpStateDialogService = heatPumpStateDialogService;

        setSizeFull();
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
            return;
        }
        accountId = authService.authenticate(token).getId();
        String idParam = event.getRouteParameters().get("sourceId").orElse(null);
        if (idParam == null) {
            event.forwardTo(ProductionSourcesView.class);
            return;
        }
        sourceId = Long.parseLong(idParam);
        buildView();
    }

    private void buildView() {
        ProductionSourceResponse source = productionSourceService.getSource(accountId, sourceId);
        VerticalLayout card = createCard();
        card.add(new H3(t("productionsource.title")));
        card.add(createSourceInfoSection(source));
        configureDeviceGrid();
        configureHeatPumpGrid();
        loadDevices();
        loadHeatPumps();
        card.add(createDeviceManagementSection());
        card.add(createCurrentStatsRow(source));
        chartDiv = createChartContainer();
        card.add(chartDiv);
        updateProductionChart(chartDiv, source);
        card.add(Divider.createDivider());
        Button deleteButton = new Button(t("button.delete"), e -> {
            deleteResourceDialog();
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        card.add(deleteButton);
        add(card);
    }

    private Component createSourceInfoSection(ProductionSourceResponse s) {
        TextField uuid = new TextField("UUID");
        uuid.setValue(s.getUuid().toString());
        uuid.setReadOnly(true);

        TextField name = new TextField(t("productionsources.field.name"));
        name.setValue(Optional.ofNullable(s.getName()).orElse(""));

        Checkbox enabled = new Checkbox(t("productionsources.field.enabled"));
        enabled.setValue(s.isEnabled());

        ComboBox<String> timezoneField = new ComboBox<>(t("productionsources.field.timezone"));
        timezoneField.setItems(ZoneId.getAvailableZoneIds());
        timezoneField.setValue(Optional.ofNullable(s.getTimezone()).orElse("UTC"));
        timezoneField.setWidthFull();

        TextField appId = new TextField(t("productionsources.field.appId"));
        appId.setValue(Optional.ofNullable(s.getAppId()).orElse(""));

        PasswordField appSecret = new PasswordField(t("productionsources.field.appSecret"));
        appSecret.setValue(Optional.ofNullable(s.getAppSecret()).orElse(""));
        appSecret.setPlaceholder("Leave empty to keep existing");

        EmailField email = new EmailField(t("productionsources.field.email"));
        email.setValue(Optional.ofNullable(s.getEmail()).orElse(""));

        PasswordField password = new PasswordField(t("productionsources.field.password"));
        password.setValue(Optional.ofNullable(s.getPassword()).orElse(""));
        password.setPlaceholder("Leave empty to keep existing");

        TextField stationId = new TextField(t("productionsources.field.stationId"));
        stationId.setValue(Optional.ofNullable(s.getStationId()).orElse(""));

        List<SiteResponse> sites = siteService.getAllSites(accountId);
        ComboBox<SiteResponse> siteBox = new ComboBox<>(t("controlTable.field.site"));
        siteBox.setItems(sites);
        siteBox.setItemLabelGenerator(SiteResponse::getName);
        siteBox.setClearButtonVisible(true);
        sites.stream()
                .filter(sr -> sr.getId().equals(s.getSiteId()))
                .findFirst()
                .ifPresent(siteBox::setValue);

        Button save = new Button("Save", e -> {
            SiteResponse site = siteBox.getValue();
            Long siteId = site != null ? site.getId() : null;

            productionSourceService.updateSource(
                    accountId,
                    sourceId,
                    name.getValue(),
                    enabled.getValue(),
                    timezoneField.getValue(),
                    emptyToNull(appId.getValue()),
                    emptyToNull(appSecret.getValue()),
                    emptyToNull(email.getValue()),
                    emptyToNull(password.getValue()),
                    emptyToNull(stationId.getValue()),
                    siteId
            );
            Notification notification = Notification.show("Saved");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout form = new FormLayout(
                uuid, enabled, name,
                appId, appSecret, email, password, stationId,
                timezoneField, siteBox
        );
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        Div wrap = styledBox();
        wrap.add(form, save);
        return wrap;
    }

    private String emptyToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private void configureDeviceGrid() {
        deviceGrid.removeAllColumns();
        deviceGrid.addColumn(d -> d.getDevice().getDeviceName())
                .setHeader(t("productionsources.devices.device"));

        deviceGrid.addColumn(ProductionSourceDeviceResponse::getDeviceChannel)
                .setHeader(t("productionsources.devices.channel"));

        deviceGrid.addColumn(ProductionSourceDeviceResponse::getTriggerKw)
                .setHeader(t("productionsources.devices.triggerKw"));

        deviceGrid.addColumn(ProductionSourceDeviceResponse::getComparisonType)
                .setHeader(t("productionsources.devices.comparisonType"));

        deviceGrid.addColumn(ProductionSourceDeviceResponse::getAction)
                .setHeader(t("productionsources.devices.action"));

        deviceGrid.addComponentColumn(d -> {
            Button delete = new Button(t("common.delete"), e -> {
                productionSourceService.removeDevice(accountId, sourceId, d.getId());
                loadDevices();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return delete;
        });

        deviceGrid.setMinHeight("200px");
        deviceGrid.setMaxHeight("250px");
    }

    private void configureHeatPumpGrid() {
        heatPumpGrid.removeAllColumns();
        heatPumpGrid.addColumn(d -> d.getDevice().getDeviceName())
                .setHeader(t("productionsources.devices.device"));
        heatPumpGrid.addColumn(d -> t("controlAction." + d.getControlAction().name()))
                .setHeader(t("productionsources.devices.action"));
        heatPumpGrid.addColumn(d -> t("comparisonType." + d.getComparisonType().name()))
                .setHeader(t("productionsources.devices.comparisonType"));
        heatPumpGrid.addColumn(ProductionSourceHeatPumpResponse::getTriggerKw)
                .setHeader(t("productionsources.devices.triggerKw"));
        heatPumpGrid.addColumn(ProductionSourceHeatPumpResponse::getStateHex)
                .setHeader(t("controlTable.grid.stateHex"));
        heatPumpGrid.addComponentColumn(d -> {
            Button delete = new Button(t("common.delete"), e -> {
                productionSourceService.removeHeatPump(accountId, sourceId, d.getId());
                loadHeatPumps();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return delete;
        }).setHeader(t("controlTable.grid.actions"));

        heatPumpGrid.setMinHeight("200px");
        heatPumpGrid.setMaxHeight("250px");
    }

    private void loadDevices() {
        deviceGrid.setItems(productionSourceService.getSourceDevices(sourceId));
    }

    private void loadHeatPumps() {
        heatPumpGrid.setItems(productionSourceService.getSourceHeatPumps(sourceId));
    }

    private Component createDeviceManagementSection() {
        Tab standardTab = new Tab(t("device.type.standard"));
        Tab heatPumpTab = new Tab(t("device.type.heatPump"));
        Tabs tabs = new Tabs(standardTab, heatPumpTab);

        VerticalLayout standardLayout = new VerticalLayout(deviceGrid, createAddDeviceLayout());
        standardLayout.setPadding(false);
        standardLayout.setSpacing(true);

        VerticalLayout heatPumpLayout = new VerticalLayout(heatPumpGrid, createAddHeatPumpLayout());
        heatPumpLayout.setPadding(false);
        heatPumpLayout.setSpacing(true);
        heatPumpLayout.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            boolean showStandard = event.getSelectedTab() == standardTab;
            standardLayout.setVisible(showStandard);
            heatPumpLayout.setVisible(!showStandard);
        });

        VerticalLayout wrapper = new VerticalLayout(
                new Div(new Text(t("productionsource.section.descriptionList"))),
                tabs,
                standardLayout,
                heatPumpLayout
        );
        wrapper.setPadding(false);
        wrapper.setSpacing(true);
        wrapper.setWidthFull();
        return wrapper;
    }

    private Component createAddDeviceLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>(t("productionsources.devices.device"));
        deviceSelect.setItems(deviceService.getAllDevices(accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.STANDARD)
                .toList());
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);

        NumberField channel = new NumberField(t("productionsources.devices.channel"));
        channel.setStep(1);
        channel.setMin(0);

        NumberField triggerKw = new NumberField(t("productionsources.devices.triggerKw"));
        triggerKw.setStep(0.1);
        triggerKw.setMin(0);

        ComboBox<ComparisonType> comparisonType = new ComboBox<>(t("productionsources.devices.comparisonType"));
        comparisonType.setItems(ComparisonType.values());
        comparisonType.setItemLabelGenerator(c -> t("comparisonType." + c.name()));
        comparisonType.setValue(ComparisonType.GREATER_THAN);

        ComboBox<ControlAction> action = new ComboBox<>(t("productionsources.devices.action"));
        action.setItems(ControlAction.values());
        action.setItemLabelGenerator(a -> t("controlAction." + a.name()));
        action.setValue(ControlAction.TURN_ON);

        Button add = new Button(t("common.add"), e -> {
            productionSourceService.addDevice(
                    accountId,
                    sourceId,
                    deviceSelect.getValue().getId(),
                    channel.getValue().intValue(),
                    BigDecimal.valueOf(triggerKw.getValue()),
                    comparisonType.getValue(),
                    action.getValue()
            );
            loadDevices();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout layout = new FormLayout(
                deviceSelect,
                channel,
                triggerKw,
                comparisonType,
                action,
                add
        );

        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 3)
        );

        return layout;
    }

    private Component createAddHeatPumpLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>(t("productionsources.devices.device"));
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        deviceSelect.setItems(deviceService.getAllDevices(accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.HEAT_PUMP)
                .toList());
        deviceSelect.setWidthFull();

        TextField stateHexField = new TextField(t("controlTable.field.stateHex"));
        stateHexField.setReadOnly(true);
        stateHexField.setWidthFull();

        Button queryStateButton = new Button(t("controlTable.button.queryState"), e -> {
            if (deviceSelect.getValue() == null) {
                Notification.show(t("controlTable.notification.selectDeviceFirst"))
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
            } else {
                openHeatPumpStateDialog(deviceSelect.getValue(), stateHexField);
            }
        });
        queryStateButton.setWidthFull();

        ComboBox<ControlAction> actionCombo = new ComboBox<>(t("productionsources.devices.action"));
        actionCombo.setItems(ControlAction.values());
        actionCombo.setItemLabelGenerator(a -> t("controlAction." + a.name()));
        actionCombo.setValue(ControlAction.TURN_ON);
        actionCombo.setWidthFull();

        ComboBox<ComparisonType> comparisonCombo = new ComboBox<>(t("productionsources.devices.comparisonType"));
        comparisonCombo.setItems(ComparisonType.values());
        comparisonCombo.setItemLabelGenerator(type -> t("comparisonType." + type.name()));
        comparisonCombo.setValue(ComparisonType.GREATER_THAN);
        comparisonCombo.setWidthFull();

        NumberField triggerKwField = new NumberField(t("productionsources.devices.triggerKw"));
        triggerKwField.setStep(0.1);
        triggerKwField.setMin(0);
        triggerKwField.setWidthFull();

        Button addButton = new Button(t("common.add"), e -> {
            if (deviceSelect.getValue() != null && !stateHexField.getValue().isEmpty() && actionCombo.getValue() != null && triggerKwField.getValue() != null) {
                productionSourceService.addHeatPump(
                        accountId,
                        sourceId,
                        deviceSelect.getValue().getId(),
                        stateHexField.getValue(),
                        actionCombo.getValue(),
                        comparisonCombo.getValue(),
                        BigDecimal.valueOf(triggerKwField.getValue())
                );
                loadHeatPumps();
                stateHexField.clear();
            }
        });
        addButton.setWidthFull();
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout formLayout = new FormLayout(deviceSelect, queryStateButton, stateHexField, actionCombo, comparisonCombo, triggerKwField, addButton);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 4)
        );

        formLayout.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        return formLayout;
    }

    private void openHeatPumpStateDialog(DeviceResponse deviceResponse, TextField stateHexField) {
        heatPumpStateDialogService.openStateDialog(deviceResponse, stateHexField);
    }

    private Component createCurrentStatsRow(ProductionSourceResponse s) {
        Component currentKw = createStatBox(t("productionsources.grid.currentKw"), s.getCurrentKw() + " kW");
        Component peakKw = createStatBox(t("productionsources.grid.peakKw"), s.getPeakKw() + " kW");

        HorizontalLayout row = new HorizontalLayout(currentKw, peakKw);
        row.setWidthFull();
        row.setSpacing(true);
        row.setPadding(false);
        row.getStyle().set("flex-wrap", "wrap").set("gap", "16px");

        currentKw.getElement().getStyle().set("flex", "1 1 300px");
        peakKw.getElement().getStyle().set("flex", "1 1 300px");

        return row;
    }

    private Component createStatBox(String titleText, String valueText) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("text-align", "center");

        H2 title = new H2(titleText);
        title.getStyle().set("margin", "0");

        Div value = new Div();
        value.setText(valueText);
        value.getStyle()
                .set("font-size", "2.5rem")
                .set("font-weight", "bold");

        wrapper.add(title, value);
        return wrapper;
    }

    private Div createChartContainer() {
        Div div = new Div();
        div.setWidthFull();
        div.setHeight("400px");
        div.getStyle().set("background", "var(--lumo-contrast-5pct)");
        return div;
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    private void deleteResourceDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("delete.confirmTitle"));
        dialog.add(t("delete.confirmDescription"));
        Button deleteButton = new Button(t("button.delete"), (e) -> {
            productionSourceService.deleteProductionSource(accountId, sourceId);
            dialog.close();
            UI.getCurrent().navigate(ProductionSourcesView.class);
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        deleteButton.getStyle().set("margin-right", "auto");
        dialog.getFooter().add(deleteButton);
        Button cancelButton = new Button(t("button.cancel"), (e) -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton);
        dialog.open();
    }

    private void updateProductionChart(
            Div chartDiv, ProductionSourceResponse sourceResponse
    ) {
        List<ProductionHistoryResponse> history = productionSourceService
                .getProductionHistory(sourceResponse.getId(), 24);

        List<String> timestamps = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        try {
            if (sourceResponse.getTimezone() != null) {
                zone = ZoneId.of(sourceResponse.getTimezone());
            }
        } catch (Exception ignored) {
        }

        DateTimeFormatter jsFormatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(zone);

        for (ProductionHistoryResponse h : history) {
            timestamps.add(jsFormatter.format(h.getCreatedAt()));
            values.add(h.getKilowatts().doubleValue());
        }

        String seriesLabel = t("productionsource.chart.series");
        String xAxisLabel = t("productionsource.chart.time");
        String yAxisLabel = t("productionsource.chart.kw");
        String chartTitle = t("productionsource.chart.title");
        String nowLabel = "";
        Double limitKw = null;

        chartDiv.getElement().executeJs("""
                            const container = this;
                        
                            function renderOrUpdate(dataX, dataY, limitKw) {
                                const now = new Date();
                                const closest = dataX.reduce((prev, curr) =>
                                    Math.abs(new Date(curr) - now) < Math.abs(new Date(prev) - now)
                                        ? curr : prev
                                );
                        
                                const annotations = {
                                    xaxis: [{
                                        x: closest,
                                        borderColor: '#00E396',
                                        label: {
                                            style: { color: '#fff', background: '#00E396' },
                                            text: $6
                                        }
                                    }]
                                };
                        
                                if (limitKw !== null) {
                                    annotations.yaxis = [{
                                        y: limitKw,
                                        borderColor: '#FF4560',
                                        label: {
                                            style: { color: '#fff', background: '#FF4560' },
                                            text: 'Limit ' + limitKw + ' kW'
                                        }
                                    }];
                                }
                        
                                if (!container.chartInstance) {
                                    const options = {
                                        chart: {
                                            type: 'line',
                                            height: '400px',
                                            zoom: { enabled: false },
                                            toolbar: { show: true }
                                        },
                                        series: [{ name: $3, data: dataY }],
                                        xaxis: {
                                            categories: dataX,
                                            title: { text: $4 },
                                            labels: { rotate: -45 }
                                        },
                                        yaxis: { title: { text: $5 } },
                                        stroke: { curve: 'smooth', width: 3 },
                                        markers: { size: 3 },
                                        tooltip: { shared: true },
                                        title: { text: $2, align: 'center' },
                                        annotations: annotations
                                    };
                                    container.chartInstance = new ApexCharts(container, options);
                                    container.chartInstance.render();
                                } else {
                                    container.chartInstance.updateOptions({
                                        xaxis: { categories: dataX },
                                        annotations: annotations
                                    });
                                    container.chartInstance.updateSeries([{ data: dataY }], true);
                                }
                            }
                        
                            renderOrUpdate($0, $1, $7);
                        """,
                timestamps,
                values,
                chartTitle,
                seriesLabel,
                xAxisLabel,
                yAxisLabel,
                nowLabel,
                limitKw
        );
    }


    private VerticalLayout createCard() {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");
        return card;
    }

    private Div styledBox() {
        Div d = new Div();
        d.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("background-color", "var(--lumo-contrast-5pct)");
        return d;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.ui = attachEvent.getUI();
        startAutoRefresh();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (scheduler != null) scheduler.shutdownNow();
        super.onDetach(detachEvent);
    }

    private void startAutoRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            // ProductionSourceResponse updated = productionSourceService.getSource(accountId, sourceId);
            // ui.access(() -> {
            //     currentKwValue.setText(updated.getCurrentKw() + " kW");
            //     peakKwValue.setText(updated.getPeakKw() + " kW");
            //     updateProductionChart(chartDiv, updated);
            // });
        }, 0, 30, TimeUnit.SECONDS);
    }

}
