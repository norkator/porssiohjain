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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.WeatherMetricType;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.HeatPumpStateDialogService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.SiteService;
import com.nitramite.porssiohjain.services.WeatherControlService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.SiteResponse;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastPointResponse;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlDeviceResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlHeatPumpResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlResponse;
import com.nitramite.porssiohjain.views.components.InfoBox;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.nitramite.porssiohjain.views.components.Divider.createDivider;

@PageTitle("Pörssiohjain - Weather Control")
@Route("weather-controls/:weatherControlId")
@PermitAll
public class WeatherControlView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final WeatherControlService weatherControlService;
    private final SiteService siteService;
    private final DeviceService deviceService;
    private final HeatPumpStateDialogService heatPumpStateDialogService;
    protected final I18nService i18n;

    private final Grid<WeatherControlDeviceResponse> deviceGrid = new Grid<>(WeatherControlDeviceResponse.class, false);
    private final Grid<WeatherControlHeatPumpResponse> heatPumpGrid = new Grid<>(WeatherControlHeatPumpResponse.class, false);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long weatherControlId;
    private Long accountId;
    private WeatherControlResponse weatherControl;
    private List<SiteResponse> sites = List.of();

    private TextField weatherTimestampField;
    private TextField temperatureField;
    private TextField windSpeedField;
    private TextField humidityField;
    private ComboBox<DeviceResponse> standardDeviceSelect;
    private NumberField standardChannelField;
    private ComboBox<WeatherMetricType> standardMetricCombo;
    private ComboBox<ComparisonType> standardComparisonCombo;
    private NumberField standardThresholdField;
    private ComboBox<ControlAction> standardActionCombo;
    private Checkbox standardPriorityRuleCheckbox;
    private Button standardSaveButton;
    private Button standardCancelButton;
    private WeatherControlDeviceResponse selectedDeviceRule;
    private ComboBox<DeviceResponse> heatPumpDeviceSelect;
    private TextField heatPumpStateHexField;
    private ComboBox<WeatherMetricType> heatPumpMetricCombo;
    private ComboBox<ComparisonType> heatPumpComparisonCombo;
    private NumberField heatPumpThresholdField;
    private Button heatPumpQueryStateButton;
    private Button heatPumpSaveButton;
    private Button heatPumpCancelButton;
    private WeatherControlHeatPumpResponse selectedHeatPumpRule;

    @Autowired
    public WeatherControlView(
            AuthService authService,
            WeatherControlService weatherControlService,
            SiteService siteService,
            DeviceService deviceService,
            HeatPumpStateDialogService heatPumpStateDialogService,
            I18nService i18n
    ) {
        this.authService = authService;
        this.weatherControlService = weatherControlService;
        this.siteService = siteService;
        this.deviceService = deviceService;
        this.heatPumpStateDialogService = heatPumpStateDialogService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        setSpacing(true);
        setPadding(true);
        deviceGrid.addItemClickListener(event -> editDeviceRule(event.getItem()));
        heatPumpGrid.addItemClickListener(event -> editHeatPumpRule(event.getItem()));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, authService)) {
            return;
        }

        try {
            AccountEntity account = ViewAuthUtils.getAuthenticatedAccount(authService, t("weatherControl.notification.sessionExpired"));
            if (account == null) {
                return;
            }
            accountId = account.getId();
            weatherControlId = Long.valueOf(event.getRouteParameters().get("weatherControlId").orElseThrow());
            sites = siteService.getAllSites(accountId);
            loadWeatherControl();
            renderView();
        } catch (Exception e) {
            removeAll();
            add(new Paragraph(t("weatherControl.errorLoad", e.getMessage())));
        }
    }

    private void loadWeatherControl() {
        weatherControl = weatherControlService.getWeatherControl(accountId, weatherControlId);
    }

    private void renderView() {
        removeAll();

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 title = new H2(t("weatherControl.detail.title", weatherControl.getName()));
        title.getStyle().set("margin-top", "0");

        configureDeviceGrid();
        configureHeatPumpGrid();

        card.add(
                title,
                createSettingsForm(),
                createDivider(),
                createWeatherInfoSection(),
                createDivider(),
                createTabsSection()
        );

        add(card);
        refreshWeatherInfo(weatherControl.getSiteId());
        loadControlDevices();
        loadControlHeatPumps();
    }

    private Component createSettingsForm() {
        if (isSharedWeatherControl()) {
            TextField nameField = new TextField(t("weatherControl.field.name"));
            nameField.setValue(weatherControl.getName());
            nameField.setReadOnly(true);
            nameField.setWidthFull();

            TextField siteField = new TextField(t("weatherControl.field.site"));
            siteField.setValue(weatherControl.getSiteName() != null ? weatherControl.getSiteName() : "");
            siteField.setReadOnly(true);
            siteField.setWidthFull();

            FormLayout formLayout = new FormLayout(nameField, siteField, createReadOnlyNotice());
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

        TextField nameField = new TextField(t("weatherControl.field.name"));
        nameField.setValue(weatherControl.getName());
        nameField.setWidthFull();

        ComboBox<SiteResponse> siteField = new ComboBox<>(t("weatherControl.field.site"));
        siteField.setItems(sites);
        siteField.setItemLabelGenerator(SiteResponse::getName);
        findSiteById(weatherControl.getSiteId()).ifPresent(siteField::setValue);
        siteField.setWidthFull();
        siteField.addValueChangeListener(event -> {
            SiteResponse selected = event.getValue();
            refreshWeatherInfo(selected != null ? selected.getId() : null);
        });

        Button saveButton = new Button(t("controlTable.button.save"), event -> {
            try {
                if (nameField.getValue() == null || nameField.getValue().isBlank()) {
                    showWarning(t("weatherControl.notification.nameEmpty"));
                    return;
                }
                if (siteField.getValue() == null) {
                    showWarning(t("weatherControl.notification.siteEmpty"));
                    return;
                }

                weatherControl = weatherControlService.updateWeatherControl(
                        accountId,
                        weatherControlId,
                        nameField.getValue().trim(),
                        siteField.getValue().getId()
                );
                Notification.show(t("weatherControl.notification.saved"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                renderView();
            } catch (Exception ex) {
                Notification.show(t("weatherControl.notification.failedSave", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout formLayout = new FormLayout(nameField, siteField, saveButton);
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

    private Component createWeatherInfoSection() {
        weatherTimestampField = createReadOnlyField(t("weatherControl.weather.field.time"), "");
        temperatureField = createReadOnlyField(t("weatherControl.weather.field.temperature"), "");
        windSpeedField = createReadOnlyField(t("weatherControl.weather.field.windSpeed"), "");
        humidityField = createReadOnlyField(t("weatherControl.weather.field.humidity"), "");

        FormLayout formLayout = new FormLayout(weatherTimestampField, temperatureField, windSpeedField, humidityField);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("900px", 4)
        );
        return formLayout;
    }

    private Component createTabsSection() {
        Tab standardTab = new Tab(t("weatherControl.tab.devices"));
        Tab heatPumpTab = new Tab(t("weatherControl.tab.heatPumps"));
        Tabs tabs = new Tabs(standardTab, heatPumpTab);
        tabs.setWidthFull();

        VerticalLayout deviceLayout = new VerticalLayout(
                deviceGrid,
                isSharedWeatherControl() ? createReadOnlyNotice() : createAddDeviceLayout()
        );
        deviceLayout.setWidthFull();
        deviceLayout.setPadding(false);
        deviceLayout.setSpacing(true);
        deviceLayout.setMargin(false);

        VerticalLayout heatPumpLayout = new VerticalLayout(
                heatPumpGrid,
                isSharedWeatherControl() ? createReadOnlyNotice() : createAddHeatPumpLayout()
        );
        heatPumpLayout.setWidthFull();
        heatPumpLayout.setPadding(false);
        heatPumpLayout.setSpacing(true);
        heatPumpLayout.setMargin(false);
        heatPumpLayout.setVisible(false);

        Map<Tab, Component> tabsToPages = new LinkedHashMap<>();
        tabsToPages.put(standardTab, deviceLayout);
        tabsToPages.put(heatPumpTab, heatPumpLayout);

        Div pages = new Div(deviceLayout, heatPumpLayout);
        pages.setWidthFull();
        pages.getStyle()
                .set("padding", "0")
                .set("margin", "0");

        tabs.addSelectedChangeListener(event -> {
            tabsToPages.values().forEach(page -> page.setVisible(false));
            Component selectedPage = tabsToPages.get(tabs.getSelectedTab());
            if (selectedPage != null) {
                selectedPage.setVisible(true);
            }
        });

        VerticalLayout tabsSection = new VerticalLayout(tabs, pages);
        tabsSection.setWidthFull();
        tabsSection.setPadding(false);
        tabsSection.setSpacing(true);
        tabsSection.setMargin(false);
        return tabsSection;
    }

    private void configureDeviceGrid() {
        deviceGrid.removeAllColumns();
        deviceGrid.addColumn(cd -> cd.getDevice().getDeviceName()).setHeader(t("controlTable.grid.deviceName"));
        deviceGrid.addColumn(WeatherControlDeviceResponse::getDeviceChannel).setHeader(t("controlTable.grid.channel"));
        deviceGrid.addColumn(cd -> t("weatherMetricType." + cd.getWeatherMetric().name())).setHeader(t("weatherControl.grid.metric"));
        deviceGrid.addColumn(cd -> cd.getComparisonType() != null ? t("comparisonType." + cd.getComparisonType().name()) : "")
                .setHeader(t("controlTable.grid.comparisonType"));
        deviceGrid.addColumn(cd -> formatDecimal(cd.getThresholdValue(), ""))
                .setHeader(t("weatherControl.grid.threshold"));
        deviceGrid.addColumn(cd -> t("controlAction." + cd.getControlAction().name())).setHeader(t("controlTable.grid.action"));
        deviceGrid.addColumn(cd -> cd.isPriorityRule() ? t("common.yes") : t("common.no")).setHeader(t("weatherControl.grid.priorityRule"));
        deviceGrid.addColumn(cd -> cd.getDevice().getUuid()).setHeader(t("controlTable.grid.uuid"));
        deviceGrid.addComponentColumn(cd -> {
            if (isSharedWeatherControl()) {
                return new Span("-");
            }
            Button delete = new Button(t("controlTable.button.delete"), event -> {
                weatherControlService.deleteWeatherControlDevice(accountId, cd.getId());
                if (selectedDeviceRule != null && selectedDeviceRule.getId().equals(cd.getId())) {
                    clearDeviceForm();
                }
                loadControlDevices();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return delete;
        }).setHeader(t("controlTable.grid.actions"));
        deviceGrid.setWidthFull();
        deviceGrid.setAllRowsVisible(true);
    }

    private void configureHeatPumpGrid() {
        heatPumpGrid.removeAllColumns();
        heatPumpGrid.addColumn(cd -> cd.getDevice().getDeviceName()).setHeader(t("controlTable.grid.deviceName"));
        heatPumpGrid.addColumn(cd -> t("weatherMetricType." + cd.getWeatherMetric().name())).setHeader(t("weatherControl.grid.metric"));
        heatPumpGrid.addColumn(cd -> t("comparisonType." + cd.getComparisonType().name())).setHeader(t("controlTable.grid.comparisonType"));
        heatPumpGrid.addColumn(cd -> formatDecimal(cd.getThresholdValue(), "")).setHeader(t("weatherControl.grid.threshold"));
        heatPumpGrid.addColumn(WeatherControlHeatPumpResponse::getStateHex).setHeader(t("controlTable.grid.stateHex"));
        heatPumpGrid.addComponentColumn(cd -> {
            Button decode = new Button(t("controlTable.button.decodeState"), event -> openHeatPumpStateHexDialog(cd.getStateHex()));
            if (isSharedWeatherControl()) {
                return decode;
            }
            Button delete = new Button(t("controlTable.button.delete"), event -> {
                weatherControlService.deleteWeatherControlHeatPump(accountId, cd.getId());
                if (selectedHeatPumpRule != null && selectedHeatPumpRule.getId().equals(cd.getId())) {
                    clearHeatPumpForm();
                }
                loadControlHeatPumps();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            HorizontalLayout actions = new HorizontalLayout(decode, delete);
            actions.setPadding(false);
            actions.setSpacing(true);
            return actions;
        }).setHeader(t("controlTable.grid.actions"));
        heatPumpGrid.setWidthFull();
        heatPumpGrid.setAllRowsVisible(true);
    }

    private void loadControlDevices() {
        deviceGrid.setItems(weatherControlService.getWeatherControlDevices(accountId, weatherControlId));
    }

    private void loadControlHeatPumps() {
        heatPumpGrid.setItems(weatherControlService.getWeatherControlHeatPumps(accountId, weatherControlId));
    }

    private Component createAddDeviceLayout() {
        standardDeviceSelect = new ComboBox<>(t("controlTable.deviceSelect"));
        standardDeviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        standardDeviceSelect.setItems(deviceService.getAllDevices(accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.STANDARD)
                .toList());
        standardDeviceSelect.setWidthFull();

        standardChannelField = new NumberField(t("controlTable.field.channel"));
        standardChannelField.setStep(1);
        standardChannelField.setWidthFull();

        standardMetricCombo = createMetricCombo();
        standardComparisonCombo = createComparisonCombo();
        standardThresholdField = new NumberField(t("weatherControl.field.threshold"));
        standardThresholdField.setStep(0.1);
        standardThresholdField.setWidthFull();
        standardActionCombo = new ComboBox<>(t("controlTable.field.action"));
        standardActionCombo.setItems(ControlAction.TURN_ON, ControlAction.TURN_OFF);
        standardActionCombo.setItemLabelGenerator(action -> t("controlAction." + action.name()));
        standardActionCombo.setValue(ControlAction.TURN_ON);
        standardActionCombo.setWidthFull();
        standardPriorityRuleCheckbox = new Checkbox(t("weatherControl.field.priorityRule"));
        standardPriorityRuleCheckbox.setWidthFull();

        standardSaveButton = new Button(t("controlTable.button.addDevice"), event -> {
            try {
                if (standardDeviceSelect.getValue() == null || standardChannelField.getValue() == null || standardMetricCombo.getValue() == null
                        || standardComparisonCombo.getValue() == null || standardThresholdField.getValue() == null || standardActionCombo.getValue() == null) {
                    showWarning(t("weatherControl.notification.ruleFieldsMissing"));
                    return;
                }

                if (selectedDeviceRule != null) {
                    weatherControlService.updateWeatherControlDevice(
                            accountId,
                            selectedDeviceRule.getId(),
                            standardDeviceSelect.getValue().getId(),
                            standardChannelField.getValue().intValue(),
                            standardMetricCombo.getValue(),
                            standardComparisonCombo.getValue(),
                            BigDecimal.valueOf(standardThresholdField.getValue()),
                            standardActionCombo.getValue(),
                            standardPriorityRuleCheckbox.getValue()
                    );
                } else {
                    weatherControlService.addDeviceToWeatherControl(
                            accountId,
                            weatherControlId,
                            standardDeviceSelect.getValue().getId(),
                            standardChannelField.getValue().intValue(),
                            standardMetricCombo.getValue(),
                            standardComparisonCombo.getValue(),
                            BigDecimal.valueOf(standardThresholdField.getValue()),
                            standardActionCombo.getValue(),
                            standardPriorityRuleCheckbox.getValue()
                    );
                }
                loadControlDevices();
                clearDeviceForm();
            } catch (Exception ex) {
                Notification.show(t("weatherControl.notification.failedSave", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        standardSaveButton.setWidthFull();

        standardCancelButton = new Button(t("common.cancel"), event -> clearDeviceForm());
        standardCancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        standardCancelButton.setWidthFull();
        standardCancelButton.setVisible(false);

        FormLayout formLayout = new FormLayout(
                standardDeviceSelect,
                standardChannelField,
                standardMetricCombo,
                standardComparisonCombo,
                standardThresholdField,
                standardActionCombo,
                standardPriorityRuleCheckbox,
                standardSaveButton,
                standardCancelButton
        );
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 4),
                new FormLayout.ResponsiveStep("900px", 6)
        );
        formLayout.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");
        clearDeviceForm();
        return new VerticalLayout(
                formLayout,
                new InfoBox(
                        t("weatherControl.priorityInfo.title"),
                        t("weatherControl.priorityInfo.description")
                )
        );
    }

    private Component createAddHeatPumpLayout() {
        heatPumpDeviceSelect = new ComboBox<>(t("controlTable.deviceSelect"));
        heatPumpDeviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        heatPumpDeviceSelect.setItems(deviceService.getAllDevices(accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.HEAT_PUMP)
                .toList());
        heatPumpDeviceSelect.setWidthFull();

        heatPumpQueryStateButton = new Button(t("controlTable.button.queryState"));
        heatPumpQueryStateButton.setWidthFull();

        heatPumpStateHexField = new TextField(t("controlTable.field.stateHex"));
        heatPumpStateHexField.setReadOnly(true);
        heatPumpStateHexField.setWidthFull();

        heatPumpQueryStateButton.addClickListener(event -> {
            if (heatPumpDeviceSelect.getValue() == null) {
                showWarning(t("controlTable.notification.selectDeviceFirst"));
                return;
            }
            openHeatPumpStateDialog(heatPumpDeviceSelect.getValue(), heatPumpStateHexField);
        });

        heatPumpMetricCombo = createMetricCombo();
        heatPumpComparisonCombo = createComparisonCombo();
        heatPumpThresholdField = new NumberField(t("weatherControl.field.threshold"));
        heatPumpThresholdField.setStep(0.1);
        heatPumpThresholdField.setWidthFull();

        heatPumpSaveButton = new Button(t("controlTable.button.addDevice"), event -> {
            try {
                if (heatPumpDeviceSelect.getValue() == null || heatPumpStateHexField.getValue().isBlank() || heatPumpMetricCombo.getValue() == null
                        || heatPumpComparisonCombo.getValue() == null || heatPumpThresholdField.getValue() == null) {
                    showWarning(t("weatherControl.notification.ruleFieldsMissing"));
                    return;
                }

                if (selectedHeatPumpRule != null) {
                    weatherControlService.updateWeatherControlHeatPump(
                            accountId,
                            selectedHeatPumpRule.getId(),
                            heatPumpDeviceSelect.getValue().getId(),
                            heatPumpStateHexField.getValue(),
                            heatPumpMetricCombo.getValue(),
                            heatPumpComparisonCombo.getValue(),
                            BigDecimal.valueOf(heatPumpThresholdField.getValue())
                    );
                } else {
                    weatherControlService.addHeatPumpToWeatherControl(
                            accountId,
                            weatherControlId,
                            heatPumpDeviceSelect.getValue().getId(),
                            heatPumpStateHexField.getValue(),
                            heatPumpMetricCombo.getValue(),
                            heatPumpComparisonCombo.getValue(),
                            BigDecimal.valueOf(heatPumpThresholdField.getValue())
                    );
                }
                loadControlHeatPumps();
                clearHeatPumpForm();
            } catch (Exception ex) {
                Notification.show(t("weatherControl.notification.failedSave", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        heatPumpSaveButton.setWidthFull();

        heatPumpCancelButton = new Button(t("common.cancel"), event -> clearHeatPumpForm());
        heatPumpCancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        heatPumpCancelButton.setWidthFull();
        heatPumpCancelButton.setVisible(false);

        FormLayout formLayout = new FormLayout(
                heatPumpDeviceSelect,
                heatPumpQueryStateButton,
                heatPumpStateHexField,
                heatPumpMetricCombo,
                heatPumpComparisonCombo,
                heatPumpThresholdField,
                heatPumpSaveButton,
                heatPumpCancelButton
        );
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 4)
        );
        formLayout.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");
        clearHeatPumpForm();
        return formLayout;
    }

    private ComboBox<WeatherMetricType> createMetricCombo() {
        ComboBox<WeatherMetricType> metricCombo = new ComboBox<>(t("weatherControl.field.metric"));
        metricCombo.setItems(WeatherMetricType.values());
        metricCombo.setItemLabelGenerator(metric -> t("weatherMetricType." + metric.name()));
        metricCombo.setWidthFull();
        return metricCombo;
    }

    private ComboBox<ComparisonType> createComparisonCombo() {
        ComboBox<ComparisonType> comparisonCombo = new ComboBox<>(t("weatherControl.field.comparisonType"));
        comparisonCombo.setItems(ComparisonType.values());
        comparisonCombo.setItemLabelGenerator(type -> t("comparisonType." + type.name()));
        comparisonCombo.setWidthFull();
        return comparisonCombo;
    }

    private void refreshWeatherInfo(Long siteId) {
        if (weatherTimestampField == null) {
            return;
        }
        if (siteId == null) {
            clearWeatherInfo();
            return;
        }

        SiteWeatherForecastPointResponse point = getCurrentWeatherPoint(siteId);
        if (point == null) {
            weatherTimestampField.setValue(t("weatherControl.weather.notAvailable"));
            temperatureField.setValue("-");
            windSpeedField.setValue("-");
            humidityField.setValue("-");
            return;
        }

        weatherTimestampField.setValue(formatInstant(point.getTime(), getSiteTimezone(siteId)));
        temperatureField.setValue(formatDecimal(point.getTemperature(), "°C"));
        windSpeedField.setValue(formatDecimal(point.getWindSpeedMs(), "m/s"));
        humidityField.setValue(formatDecimal(point.getHumidity(), "%"));
    }

    private void clearWeatherInfo() {
        weatherTimestampField.clear();
        temperatureField.clear();
        windSpeedField.clear();
        humidityField.clear();
    }

    private SiteWeatherForecastPointResponse getCurrentWeatherPoint(Long siteId) {
        SiteWeatherForecastResponse response = weatherControlService.getStoredWeatherForecast(accountId, weatherControlId, null, null);
        if (response.getPoints() == null || response.getPoints().isEmpty()) {
            return null;
        }

        Instant now = Instant.now();
        return response.getPoints().stream()
                .min((left, right) -> {
                    long leftDistance = Math.abs(left.getTime().toEpochMilli() - now.toEpochMilli());
                    long rightDistance = Math.abs(right.getTime().toEpochMilli() - now.toEpochMilli());
                    return Long.compare(leftDistance, rightDistance);
                })
                .orElse(null);
    }

    private Optional<SiteResponse> findSiteById(Long siteId) {
        return sites.stream().filter(site -> site.getId().equals(siteId)).findFirst();
    }

    private String getSiteTimezone(Long siteId) {
        return findSiteById(siteId)
                .map(SiteResponse::getTimezone)
                .filter(timezone -> timezone != null && !timezone.isBlank())
                .or(() -> Optional.ofNullable(weatherControl.getSiteTimezone())
                        .filter(timezone -> !timezone.isBlank()))
                .orElse("Europe/Helsinki");
    }

    private void openHeatPumpStateDialog(DeviceResponse deviceResponse, TextField stateHexField) {
        heatPumpStateDialogService.openStateDialog(deviceResponse, stateHexField);
    }

    private void openHeatPumpStateHexDialog(String stateHex) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("controlTable.dialog.decodeState.title"));
        dialog.setWidth("900px");
        dialog.setMaxWidth("95vw");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setWidthFull();
        dialogLayout.add(
                new Paragraph(t("controlTable.dialog.decodeState.instructions")),
                heatPumpStateDialogService.createAcStateInfoContentFromHex(stateHex)
        );

        dialog.add(dialogLayout);
        dialog.getFooter().add(new Button(t("common.cancel"), event -> dialog.close()));
        dialog.open();
    }

    private void editHeatPumpRule(WeatherControlHeatPumpResponse rule) {
        if (isSharedWeatherControl()) {
            return;
        }
        selectedHeatPumpRule = rule;
        if (heatPumpDeviceSelect == null) {
            return;
        }

        DeviceResponse matchingDevice = deviceService.getAllDevices(accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.HEAT_PUMP)
                .filter(device -> device.getId().equals(rule.getDeviceId()))
                .findFirst()
                .orElse(null);

        heatPumpDeviceSelect.setValue(matchingDevice);
        heatPumpStateHexField.setValue(rule.getStateHex() != null ? rule.getStateHex() : "");
        heatPumpMetricCombo.setValue(rule.getWeatherMetric());
        heatPumpComparisonCombo.setValue(rule.getComparisonType());
        heatPumpThresholdField.setValue(rule.getThresholdValue() != null ? rule.getThresholdValue().doubleValue() : null);
        heatPumpSaveButton.setText(t("controlTable.button.save"));
        heatPumpCancelButton.setVisible(true);
    }

    private void editDeviceRule(WeatherControlDeviceResponse rule) {
        if (isSharedWeatherControl()) {
            return;
        }
        selectedDeviceRule = rule;
        if (standardDeviceSelect == null) {
            return;
        }

        DeviceResponse matchingDevice = deviceService.getAllDevices(accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.STANDARD)
                .filter(device -> device.getId().equals(rule.getDeviceId()))
                .findFirst()
                .orElse(null);

        standardDeviceSelect.setValue(matchingDevice);
        standardChannelField.setValue(rule.getDeviceChannel() != null ? rule.getDeviceChannel().doubleValue() : null);
        standardMetricCombo.setValue(rule.getWeatherMetric());
        standardComparisonCombo.setValue(rule.getComparisonType());
        standardThresholdField.setValue(rule.getThresholdValue() != null ? rule.getThresholdValue().doubleValue() : null);
        standardActionCombo.setValue(rule.getControlAction());
        standardPriorityRuleCheckbox.setValue(rule.isPriorityRule());
        standardSaveButton.setText(t("controlTable.button.save"));
        standardCancelButton.setVisible(true);
    }

    private void clearDeviceForm() {
        selectedDeviceRule = null;
        if (standardDeviceSelect == null) {
            return;
        }

        deviceGrid.deselectAll();
        standardDeviceSelect.clear();
        standardChannelField.clear();
        standardMetricCombo.clear();
        standardComparisonCombo.clear();
        standardThresholdField.clear();
        standardActionCombo.setValue(ControlAction.TURN_ON);
        standardPriorityRuleCheckbox.setValue(false);
        standardSaveButton.setText(t("controlTable.button.addDevice"));
        standardCancelButton.setVisible(false);
    }

    private void clearHeatPumpForm() {
        selectedHeatPumpRule = null;
        if (heatPumpDeviceSelect == null) {
            return;
        }

        heatPumpGrid.deselectAll();
        heatPumpDeviceSelect.clear();
        heatPumpStateHexField.clear();
        heatPumpMetricCombo.clear();
        heatPumpComparisonCombo.clear();
        heatPumpThresholdField.clear();
        heatPumpSaveButton.setText(t("controlTable.button.addDevice"));
        heatPumpCancelButton.setVisible(false);
    }

    private TextField createReadOnlyField(String label, String value) {
        TextField field = new TextField(label);
        field.setReadOnly(true);
        field.setWidthFull();
        field.setValue(value != null ? value : "");
        return field;
    }


    private String formatDecimal(BigDecimal value, String unit) {
        if (value == null) {
            return "-";
        }
        String number = value.stripTrailingZeros().toPlainString();
        return unit == null || unit.isBlank() ? number : number + " " + unit;
    }

    private String formatInstant(Instant instant, String timezone) {
        return instant != null
                ? ZonedDateTime.ofInstant(instant, ZoneId.of(timezone)).format(formatter)
                : "-";
    }

    private void showWarning(String message) {
        Notification.show(message).addThemeVariants(NotificationVariant.LUMO_WARNING);
    }

    private boolean isSharedWeatherControl() {
        return weatherControl != null && Boolean.TRUE.equals(weatherControl.getShared());
    }

    private Span createReadOnlyNotice() {
        Span notice = new Span(t("weatherControl.shared.readOnly"));
        notice.getElement().getThemeList().add("badge");
        notice.getElement().getThemeList().add("warning");
        return notice;
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
