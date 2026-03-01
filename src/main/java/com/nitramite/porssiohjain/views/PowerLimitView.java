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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ContractType;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.*;
import com.nitramite.porssiohjain.views.components.Divider;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
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
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@JsModule("./js/apexcharts.min.js")
@PageTitle("Pörssiohjain - Power Limit")
@Route("power-limit/:powerLimitId")
@PermitAll
public class PowerLimitView extends VerticalLayout implements BeforeEnterObserver {

    private final I18nService i18n;
    private final AuthService authService;
    private final PowerLimitService powerLimitService;
    private final DeviceService deviceService;
    private final SiteService siteService;
    private final ElectricityContractRepository contractRepository;

    private Long powerLimitId;
    private final Grid<PowerLimitDeviceResponse> deviceGrid = new Grid<>(PowerLimitDeviceResponse.class, false);
    private Div lastTotalKwh;
    private Div peakKwValue;
    private Div currentKwValue;
    private Div quarterSumValue;
    private Div chartDiv;
    private ScheduledExecutorService scheduler;
    private UI ui;
    private Long accountId;

    @Autowired
    public PowerLimitView(
            AuthService authService,
            I18nService i18n,
            PowerLimitService powerLimitService,
            DeviceService deviceService,
            SiteService siteService,
            ElectricityContractRepository contractRepository
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.powerLimitService = powerLimitService;
        this.deviceService = deviceService;
        this.siteService = siteService;
        this.contractRepository = contractRepository;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification notification = Notification.show(t("powerlimit.notification.sessionExpired"));
            notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
            UI.getCurrent().navigate(LoginView.class);
            return;
        }

        authService.authenticate(token);

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

        String idParam = event.getRouteParameters().get("powerLimitId").orElse(null);
        if (idParam == null) {
            event.forwardTo(PowerLimitsView.class);
            return;
        }

        try {
            powerLimitId = Long.parseLong(idParam);
        } catch (NumberFormatException e) {
            event.forwardTo(PowerLimitsView.class);
            return;
        }

        buildView();
    }

    private void buildView() {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H3 title = new H3(t("powerlimit.title.modify"));
        title.getStyle().set("margin-top", "0");
        card.add(title);

        Paragraph subtitle = new Paragraph(t("powerlimit.subtitle"));
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");
        card.add(subtitle);

        PowerLimitResponse powerLimit = powerLimitService.getPowerLimit(getAccountId(), powerLimitId);

        card.add(createPowerLimitInfoSection(powerLimit));

        configureDeviceGrid();
        loadPowerLimitDevices();
        card.add(deviceGrid);
        card.add(createAddDeviceLayout());

        card.add(createCurrentUsageRow(powerLimit));

        chartDiv = new Div();
        chartDiv.setId("kw-history-chart");
        chartDiv.setWidthFull();
        chartDiv.setHeight("400px");
        chartDiv.getStyle()
                .set("margin-top", "32px")
                .set("margin-bottom", "32px")
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("box-sizing", "border-box");

        card.add(chartDiv);

        updatePowerLimitHistoryChart(chartDiv, powerLimit);

        card.add(Divider.createDivider());

        Button deleteButton = new Button(t("button.delete"), e -> {
            deleteResourceDialog();
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        card.add(deleteButton);

        add(card);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    private void configureDeviceGrid() {
        deviceGrid.removeAllColumns();

        deviceGrid.addColumn(d -> d.getDevice().getDeviceName())
                .setHeader(t("controlTable.grid.deviceName"));

        deviceGrid.addColumn(PowerLimitDeviceResponse::getDeviceChannel)
                .setHeader(t("controlTable.grid.channel"));

        deviceGrid.addColumn(d -> d.getDevice().getUuid())
                .setHeader(t("controlTable.grid.uuid"));

        deviceGrid.addComponentColumn(d -> {
            Button delete = new Button(t("controlTable.button.delete"), e -> {
                powerLimitService.deletePowerLimitDevice(getAccountId(), d.getId());
                loadPowerLimitDevices();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return delete;
        }).setHeader(t("controlTable.grid.actions"));

        deviceGrid.setMinHeight("200px");
        deviceGrid.setMaxHeight("250px");
    }


    private void loadPowerLimitDevices() {
        deviceGrid.setItems(powerLimitService.getPowerLimitDevices(powerLimitId));
    }

    private Component createAddDeviceLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>(t("controlTable.deviceSelect"));
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        deviceSelect.setItems(deviceService.getAllDevicesForPowerLimitId(powerLimitId));
        deviceSelect.setWidthFull();

        NumberField channelField = new NumberField(t("controlTable.field.channel"));
        channelField.setStep(1);
        channelField.setWidthFull();

        Button addButton = new Button(t("controlTable.button.addDevice"), e -> {
            if (deviceSelect.getValue() != null && channelField.getValue() != null) {
                powerLimitService.addDeviceToPowerLimit(
                        getAccountId(),
                        powerLimitId,
                        deviceSelect.getValue().getId(),
                        channelField.getValue().intValue()
                );
                loadPowerLimitDevices();
            }
        });
        addButton.setWidthFull();

        FormLayout formLayout = new FormLayout(deviceSelect, channelField, addButton);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 3)
        );

        formLayout.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        return formLayout;
    }

    private Long getAccountId() {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification notification = Notification.show(t("controlTable.sessionExpired"));
            notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
            UI.getCurrent().navigate(LoginView.class);
        }

        AccountEntity account = authService.authenticate(token);
        return account.getId();
    }

    private Component createPowerLimitInfoSection(PowerLimitResponse p) {
        TextField uuidField = new TextField(t("powerlimit.field.uuid"));
        uuidField.setValue(p.getUuid().toString());
        uuidField.setReadOnly(true);
        uuidField.setWidthFull();

        uuidField.getElement().setAttribute("readonly", "true");
        uuidField.getElement().setProperty("title", p.getUuid().toString());

        TextField nameField = new TextField(t("powerlimit.field.name"));
        nameField.setValue(p.getName());
        nameField.setWidthFull();

        NumberField limitKwField = new NumberField(t("powerlimit.field.limitKw"));
        limitKwField.setStep(0.1);
        limitKwField.setValue(p.getLimitKw().doubleValue());
        limitKwField.setWidthFull();

        ComboBox<Integer> limitIntervalField = new ComboBox<>(t("powerlimit.field.limitInterval"));
        limitIntervalField.setItems(15, 60);
        limitIntervalField.setValue(p.getLimitIntervalMinutes());
        limitIntervalField.setWidthFull();

        Checkbox enabledField = new Checkbox(t("powerlimit.field.enabled"));
        enabledField.setValue(p.isEnabled());
        enabledField.getStyle().set("margin-top", "12px");

        ComboBox<String> timezoneField = new ComboBox<>(t("powerlimit.field.timezone"));
        timezoneField.setItems(ZoneId.getAvailableZoneIds());
        timezoneField.setValue(p.getTimezone());
        timezoneField.setWidthFull();

        Checkbox notifyEnabledField = new Checkbox(t("powerlimit.field.notifyEnabled"));
        notifyEnabledField.setValue(p.isNotifyEnabled());
        notifyEnabledField.getStyle().set("margin-top", "12px");

        List<SiteResponse> sites = siteService.getAllSites(getAccountId());
        ComboBox<SiteResponse> siteBox = new ComboBox<>(t("controlTable.field.site"));
        siteBox.setItems(sites);
        siteBox.setItemLabelGenerator(SiteResponse::getName);
        siteBox.setClearButtonVisible(true);
        sites.stream()
                .filter(sr -> sr.getId().equals(p.getSiteId()))
                .findFirst()
                .ifPresent(siteBox::setValue);

        List<ElectricityContractEntity> contracts =
                contractRepository.findByAccountId(getAccountId());
        ComboBox<ElectricityContractEntity> energyContractCombo =
                new ComboBox<>(t("controlTable.field.energyContract"));
        ComboBox<ElectricityContractEntity> transferContractCombo =
                new ComboBox<>(t("controlTable.field.transferContract"));
        energyContractCombo.setItems(
                contracts.stream()
                        .filter(c -> c.getType() == ContractType.ENERGY)
                        .toList()
        );
        transferContractCombo.setItems(
                contracts.stream()
                        .filter(c -> c.getType() == ContractType.TRANSFER)
                        .toList()
        );
        if (p.getEnergyContractId() != null) {
            contracts.stream()
                    .filter(c -> c.getId().equals(p.getEnergyContractId()))
                    .findFirst()
                    .ifPresent(energyContractCombo::setValue);
        }
        if (p.getTransferContractId() != null) {
            contracts.stream()
                    .filter(c -> c.getId().equals(p.getTransferContractId()))
                    .findFirst()
                    .ifPresent(transferContractCombo::setValue);
        }
        energyContractCombo.setItemLabelGenerator(ElectricityContractEntity::getName);
        transferContractCombo.setItemLabelGenerator(ElectricityContractEntity::getName);
        energyContractCombo.setClearButtonVisible(true);
        transferContractCombo.setClearButtonVisible(true);
        energyContractCombo.setWidthFull();
        transferContractCombo.setWidthFull();

        Button saveButton = new Button(t("powerlimit.button.save"), e -> {
            SiteResponse site = siteBox.getValue();
            Long siteId = site != null ? site.getId() : null;
            ElectricityContractEntity energy = energyContractCombo.getValue();
            ElectricityContractEntity transfer = transferContractCombo.getValue();
            Long energyId = energy != null ? energy.getId() : null;
            Long transferId = transfer != null ? transfer.getId() : null;

            powerLimitService.updatePowerLimit(
                    getAccountId(),
                    p.getId(),
                    nameField.getValue(),
                    BigDecimal.valueOf(limitKwField.getValue()),
                    limitIntervalField.getValue(),
                    enabledField.getValue(),
                    notifyEnabledField.getValue(),
                    timezoneField.getValue(),
                    siteId,
                    energyId,
                    transferId
            );

            Notification notification = Notification.show(t("powerlimit.notification.saved"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Div formDiv = new Div();

        FormLayout form = new FormLayout(
                uuidField,
                nameField,
                limitKwField,
                limitIntervalField,
                enabledField,
                notifyEnabledField,
                timezoneField,
                siteBox,
                energyContractCombo,
                transferContractCombo
        );

        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        formDiv.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        formDiv.add(form, saveButton);
        return formDiv;
    }

    private Component createCurrentUsageRow(
            PowerLimitResponse powerLimit
    ) {
        Component lastTotalKwh = createLastTotalKwhSection(powerLimit);
        Component peakKw = createPeakKwSection(powerLimit);
        Component currentKw = createCurrentKwSection(powerLimit);
        Component quarterSum = createCurrentIntervalSumSection(
                powerLimitService.getCurrentIntervalSum(
                        getAccountId(),
                        powerLimit.getId()
                )
        );
        HorizontalLayout row = new HorizontalLayout(lastTotalKwh, peakKw, currentKw, quarterSum);
        row.setWidthFull();
        row.setSpacing(true);
        row.setPadding(false);
        row.getStyle().set("flex-wrap", "wrap").set("gap", "16px");
        lastTotalKwh.getElement().getStyle().set("flex", "1 1 200px");
        peakKw.getElement().getStyle().set("flex", "1 1 200px");
        currentKw.getElement().getStyle().set("flex", "1 1 200px");
        quarterSum.getElement().getStyle().set("flex", "1 1 200px");
        return row;
    }

    private Component createLastTotalKwhSection(PowerLimitResponse p) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("padding", "14px")
                .set("border-radius", "12px")
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("text-align", "center");
        H2 title = new H2(t("powerlimit.lastTotalKwh"));
        title.getStyle().set("margin", "0");
        lastTotalKwh = new Div();
        lastTotalKwh.setText(p.getLastTotalKwh() + " kW");
        lastTotalKwh.getStyle()
                .set("font-size", "2rem")
                .set("font-weight", "bold");
        wrapper.add(title, lastTotalKwh);
        return wrapper;
    }

    private Component createPeakKwSection(PowerLimitResponse p) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("padding", "14px")
                .set("border-radius", "12px")
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("text-align", "center");
        H2 title = new H2(t("powerlimit.peakUsage"));
        title.getStyle().set("margin", "0");
        peakKwValue = new Div();
        peakKwValue.setText(p.getPeakKw() + " kW");
        peakKwValue.getStyle()
                .set("font-size", "2rem")
                .set("font-weight", "bold");
        wrapper.add(title, peakKwValue);
        return wrapper;
    }

    private Component createCurrentKwSection(PowerLimitResponse p) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("padding", "14px")
                .set("border-radius", "12px")
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("text-align", "center");
        H2 title = new H2(t("powerlimit.currentUsage"));
        title.getStyle().set("margin", "0");
        currentKwValue = new Div();
        currentKwValue.setText(p.getCurrentKw() + " kW");
        currentKwValue.getStyle()
                .set("font-size", "2rem")
                .set("font-weight", "bold");
        wrapper.add(title, currentKwValue);
        return wrapper;
    }

    private Component createCurrentIntervalSumSection(
            Optional<BigDecimal> cAvg
    ) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("padding", "14px")
                .set("border-radius", "12px")
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("text-align", "center");
        H2 title = new H2(t("powerlimit.cMinSum"));
        title.getStyle().set("margin", "0");
        quarterSumValue = new Div();
        quarterSumValue.setText(
                cAvg.map(v -> v + " kW").orElse("N/A")
        );
        quarterSumValue.getStyle()
                .set("font-size", "2rem")
                .set("font-weight", "bold");
        wrapper.add(title, quarterSumValue);
        return wrapper;
    }

    private void deleteResourceDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("delete.confirmTitle"));
        dialog.add(t("delete.confirmDescription"));
        Button deleteButton = new Button(t("button.delete"), (e) -> {
            powerLimitService.deletePowerLimit(getAccountId(), powerLimitId);
            dialog.close();
            UI.getCurrent().navigate(PowerLimitsView.class);
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        deleteButton.getStyle().set("margin-right", "auto");
        dialog.getFooter().add(deleteButton);
        Button cancelButton = new Button(t("button.cancel"), (e) -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton);
        dialog.open();
    }

    private void updatePowerLimitHistoryChart(
            Div chartDiv,
            PowerLimitResponse powerLimit
    ) {
        List<PowerLimitHistoryResponse> history =
                powerLimitService.getPowerLimitHistoryWithInterval(
                        getAccountId(),
                        powerLimit.getId(),
                        24
                );

        List<String> timestamps = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        ZoneId zone = ZoneId.systemDefault();
        try {
            if (powerLimit.getTimezone() != null) {
                zone = ZoneId.of(powerLimit.getTimezone());
            }
        } catch (Exception ignored) {
        }

        DateTimeFormatter jsFormatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(zone);

        for (PowerLimitHistoryResponse h : history) {
            timestamps.add(jsFormatter.format(h.getCreatedAt()));
            values.add(h.getKilowatts().doubleValue());
        }

        String seriesLabel = t("powerlimit.chart.series");
        String xAxisLabel = t("powerlimit.chart.time");
        String yAxisLabel = t("powerlimit.chart.kw");
        String chartTitle = t("powerlimit.chart.title");
        String nowLabel = ""; // t("powerlimit.chart.now");
        String limitLabel = t("powerlimit.chart.limit");

        Double limitKw = powerLimit.getLimitKw() != null
                ? powerLimit.getLimitKw().doubleValue()
                : null;

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
                                        text: $8 + ' ' + limitKw + ' kW'
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
                                    series: [{
                                        name: $3,
                                        data: dataY
                                    }],
                                    xaxis: {
                                        categories: dataX,
                                        title: { text: $4 },
                                        labels: { rotate: -45 }
                                    },
                                    yaxis: {
                                        title: { text: $5 }
                                    },
                                    stroke: {
                                        curve: 'smooth',
                                        width: 3
                                    },
                                    markers: { size: 3 },
                                    tooltip: { shared: true },
                                    title: {
                                        text: $2,
                                        align: 'center'
                                    },
                                    annotations: annotations
                                };
                        
                                container.chartInstance = new ApexCharts(container, options);
                                container.chartInstance.render();
                            } else {
                                container.chartInstance.updateOptions({
                                    xaxis: { categories: dataX },
                                    annotations: annotations
                                });
                                container.chartInstance.updateSeries([
                                    { data: dataY }
                                ], true);
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
                limitKw,
                limitLabel
        );
    }

    private void startAutoRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (ui == null || !ui.isAttached()) {
                    return;
                }
                PowerLimitResponse updated = powerLimitService.getPowerLimit(accountId, powerLimitId);
                Optional<BigDecimal> sum = powerLimitService.getCurrentIntervalSum(
                        accountId,
                        powerLimitId
                );
                ui.access(() -> {
                    lastTotalKwh.setText(updated.getLastTotalKwh() + " kW");
                    peakKwValue.setText(updated.getPeakKw() + " kW");
                    currentKwValue.setText(updated.getCurrentKw() + " kW");
                    quarterSumValue.setText(
                            sum.map(a -> a + " kW").orElse("N/A")
                    );
                    updatePowerLimitHistoryChart(chartDiv, updated);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 30, TimeUnit.SECONDS);
    }


    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.ui = attachEvent.getUI();
        this.accountId = getAccountId();
        startAutoRefresh();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        super.onDetach(detachEvent);
    }

}
