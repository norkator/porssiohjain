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
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.WeatherMetricType;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
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
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateDecodedResponse;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexDecoderService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateResponse;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateService;
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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
    private final ToshibaAcStateService toshibaAcStateService;
    private final ToshibaAcStateHexDecoderService toshibaAcStateHexDecoderService;
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final DeviceRepository deviceRepository;
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

    @Autowired
    public WeatherControlView(
            AuthService authService,
            WeatherControlService weatherControlService,
            SiteService siteService,
            DeviceService deviceService,
            ToshibaAcStateService toshibaAcStateService,
            ToshibaAcStateHexDecoderService toshibaAcStateHexDecoderService,
            DeviceAcDataRepository deviceAcDataRepository,
            DeviceRepository deviceRepository,
            I18nService i18n
    ) {
        this.authService = authService;
        this.weatherControlService = weatherControlService;
        this.siteService = siteService;
        this.deviceService = deviceService;
        this.toshibaAcStateService = toshibaAcStateService;
        this.toshibaAcStateHexDecoderService = toshibaAcStateHexDecoderService;
        this.deviceAcDataRepository = deviceAcDataRepository;
        this.deviceRepository = deviceRepository;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        setSpacing(true);
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
            return;
        }

        try {
            AccountEntity account = authService.authenticate(token);
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

        VerticalLayout deviceLayout = new VerticalLayout(deviceGrid, createAddDeviceLayout());
        deviceLayout.setPadding(false);
        deviceLayout.setSpacing(true);

        VerticalLayout heatPumpLayout = new VerticalLayout(heatPumpGrid, createAddHeatPumpLayout());
        heatPumpLayout.setPadding(false);
        heatPumpLayout.setSpacing(true);
        heatPumpLayout.setVisible(false);

        Map<Tab, Component> tabsToPages = new LinkedHashMap<>();
        tabsToPages.put(standardTab, deviceLayout);
        tabsToPages.put(heatPumpTab, heatPumpLayout);

        Div pages = new Div(deviceLayout, heatPumpLayout);
        pages.setWidthFull();

        tabs.addSelectedChangeListener(event -> {
            tabsToPages.values().forEach(page -> page.setVisible(false));
            Component selectedPage = tabsToPages.get(tabs.getSelectedTab());
            if (selectedPage != null) {
                selectedPage.setVisible(true);
            }
        });

        return new VerticalLayout(tabs, pages);
    }

    private void configureDeviceGrid() {
        deviceGrid.removeAllColumns();
        deviceGrid.addColumn(cd -> cd.getDevice().getDeviceName()).setHeader(t("controlTable.grid.deviceName"));
        deviceGrid.addColumn(WeatherControlDeviceResponse::getDeviceChannel).setHeader(t("controlTable.grid.channel"));
        deviceGrid.addColumn(cd -> t("weatherMetricType." + cd.getWeatherMetric().name())).setHeader(t("weatherControl.grid.metric"));
        deviceGrid.addColumn(cd -> cd.getDevice().getUuid()).setHeader(t("controlTable.grid.uuid"));
        deviceGrid.addComponentColumn(cd -> {
            Button delete = new Button(t("controlTable.button.delete"), event -> {
                weatherControlService.deleteWeatherControlDevice(accountId, cd.getId());
                loadControlDevices();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return delete;
        }).setHeader(t("controlTable.grid.actions"));
        deviceGrid.setMaxHeight("200px");
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
            Button delete = new Button(t("controlTable.button.delete"), event -> {
                weatherControlService.deleteWeatherControlHeatPump(accountId, cd.getId());
                loadControlHeatPumps();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            HorizontalLayout actions = new HorizontalLayout(decode, delete);
            actions.setPadding(false);
            actions.setSpacing(true);
            return actions;
        }).setHeader(t("controlTable.grid.actions"));
        heatPumpGrid.setMaxHeight("200px");
    }

    private void loadControlDevices() {
        deviceGrid.setItems(weatherControlService.getWeatherControlDevices(accountId, weatherControlId));
    }

    private void loadControlHeatPumps() {
        heatPumpGrid.setItems(weatherControlService.getWeatherControlHeatPumps(accountId, weatherControlId));
    }

    private Component createAddDeviceLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>(t("controlTable.deviceSelect"));
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        deviceSelect.setItems(deviceService.getAllDevices(accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.STANDARD)
                .toList());
        deviceSelect.setWidthFull();

        NumberField channelField = new NumberField(t("controlTable.field.channel"));
        channelField.setStep(1);
        channelField.setWidthFull();

        ComboBox<WeatherMetricType> metricCombo = createMetricCombo();

        Button addButton = new Button(t("controlTable.button.addDevice"), event -> {
            try {
                if (deviceSelect.getValue() == null || channelField.getValue() == null || metricCombo.getValue() == null) {
                    showWarning(t("weatherControl.notification.ruleFieldsMissing"));
                    return;
                }

                weatherControlService.addDeviceToWeatherControl(
                        accountId,
                        weatherControlId,
                        deviceSelect.getValue().getId(),
                        channelField.getValue().intValue(),
                        metricCombo.getValue()
                );
                loadControlDevices();
                channelField.clear();
                metricCombo.clear();
            } catch (Exception ex) {
                Notification.show(t("weatherControl.notification.failedSave", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        addButton.setWidthFull();

        FormLayout formLayout = new FormLayout(deviceSelect, channelField, metricCombo, addButton);
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

    private Component createAddHeatPumpLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>(t("controlTable.deviceSelect"));
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        deviceSelect.setItems(deviceService.getAllDevices(accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.HEAT_PUMP)
                .toList());
        deviceSelect.setWidthFull();

        Button queryStateButton = new Button(t("controlTable.button.queryState"));
        queryStateButton.setWidthFull();

        TextField stateHexField = new TextField(t("controlTable.field.stateHex"));
        stateHexField.setReadOnly(true);
        stateHexField.setWidthFull();

        queryStateButton.addClickListener(event -> {
            if (deviceSelect.getValue() == null) {
                showWarning(t("controlTable.notification.selectDeviceFirst"));
                return;
            }
            openHeatPumpStateDialog(deviceSelect.getValue(), stateHexField);
        });

        ComboBox<WeatherMetricType> metricCombo = createMetricCombo();
        ComboBox<ComparisonType> comparisonCombo = createComparisonCombo();
        NumberField thresholdField = new NumberField(t("weatherControl.field.threshold"));
        thresholdField.setStep(0.1);
        thresholdField.setWidthFull();

        Button addButton = new Button(t("controlTable.button.addDevice"), event -> {
            try {
                if (deviceSelect.getValue() == null || stateHexField.getValue().isBlank() || metricCombo.getValue() == null
                        || comparisonCombo.getValue() == null || thresholdField.getValue() == null) {
                    showWarning(t("weatherControl.notification.ruleFieldsMissing"));
                    return;
                }

                weatherControlService.addHeatPumpToWeatherControl(
                        accountId,
                        weatherControlId,
                        deviceSelect.getValue().getId(),
                        stateHexField.getValue(),
                        metricCombo.getValue(),
                        comparisonCombo.getValue(),
                        BigDecimal.valueOf(thresholdField.getValue())
                );
                loadControlHeatPumps();
                stateHexField.clear();
                metricCombo.clear();
                comparisonCombo.clear();
                thresholdField.clear();
            } catch (Exception ex) {
                Notification.show(t("weatherControl.notification.failedSave", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        addButton.setWidthFull();

        FormLayout formLayout = new FormLayout(deviceSelect, queryStateButton, stateHexField, metricCombo, comparisonCombo, thresholdField, addButton);
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

    private ComboBox<WeatherMetricType> createMetricCombo() {
        ComboBox<WeatherMetricType> metricCombo = new ComboBox<>(t("weatherControl.field.metric"));
        metricCombo.setItems(WeatherMetricType.values());
        metricCombo.setItemLabelGenerator(metric -> t("weatherMetricType." + metric.name()));
        metricCombo.setWidthFull();
        return metricCombo;
    }

    private ComboBox<ComparisonType> createComparisonCombo() {
        ComboBox<ComparisonType> comparisonCombo = new ComboBox<>(t("controlTable.field.comparisonType"));
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

        weatherTimestampField.setValue(formatInstant(point.getTime()));
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
        SiteWeatherForecastResponse response = siteService.getStoredSiteWeatherForecast(accountId, siteId, null, null);
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

    private void openHeatPumpStateDialog(DeviceResponse deviceResponse, TextField stateHexField) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("controlTable.dialog.queryState.title"));
        dialog.setWidth("900px");
        dialog.setMaxWidth("95vw");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.add(new Paragraph(t("controlTable.dialog.queryState.instructions")));
        dialogLayout.setWidthFull();

        Div stateInfoDiv = new Div();
        stateInfoDiv.setVisible(false);
        stateInfoDiv.setWidthFull();

        Button actionButton = new Button(t("controlTable.dialog.queryState.actionButton"), event -> {
            try {
                DeviceEntity deviceEntity = deviceRepository.findById(deviceResponse.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Device not found"));
                DeviceAcDataEntity acData = deviceAcDataRepository.findByDevice(deviceEntity)
                        .orElseThrow(() -> new IllegalArgumentException("AC data not found for device"));

                ToshibaAcStateResponse response = toshibaAcStateService.getAcState(acData);
                if (response != null && response.isSuccess() && response.getResObj() != null) {
                    String stateHex = response.getResObj().getAcStateData();
                    stateHexField.setValue(stateHex);
                    stateInfoDiv.removeAll();
                    stateInfoDiv.add(createAcStateInfoContent(response));
                    stateInfoDiv.setVisible(true);
                    Notification.show(t("controlTable.dialog.queryState.queried"))
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } else {
                    Notification.show(t("weatherControl.notification.failedSave", response != null ? response.getMessage() : "Empty response"))
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            } catch (Exception ex) {
                Notification.show(t("weatherControl.notification.failedSave", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        dialogLayout.add(actionButton, stateInfoDiv);

        dialog.add(dialogLayout);

        Button saveButton = new Button(t("common.save"), event -> dialog.close());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(saveButton);

        Button cancelButton = new Button(t("common.cancel"), event -> {
            stateHexField.clear();
            dialog.close();
        });
        dialog.getFooter().add(cancelButton);

        dialog.open();
    }

    private void openHeatPumpStateHexDialog(String stateHex) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("controlTable.dialog.decodeState.title"));
        dialog.setWidth("900px");
        dialog.setMaxWidth("95vw");

        ToshibaAcStateResponse response = new ToshibaAcStateResponse();
        response.setSuccess(true);

        ToshibaAcStateResponse.ResObj resObj = new ToshibaAcStateResponse.ResObj();
        resObj.setAcStateData(stateHex);
        resObj.setDecodedAcState(toshibaAcStateHexDecoderService.decode(stateHex));
        response.setResObj(resObj);

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setWidthFull();
        dialogLayout.add(
                new Paragraph(t("controlTable.dialog.decodeState.instructions")),
                createAcStateInfoContent(response)
        );

        dialog.add(dialogLayout);
        dialog.getFooter().add(new Button(t("common.cancel"), event -> dialog.close()));
        dialog.open();
    }

    private Component createAcStateInfoContent(ToshibaAcStateResponse response) {
        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);
        content.setWidthFull();

        ToshibaAcStateResponse.ResObj resObj = response.getResObj();
        ToshibaAcStateDecodedResponse decoded = resObj.getDecodedAcState();

        TextArea hexArea = new TextArea(t("controlTable.dialog.queryState.field.stateHex"));
        hexArea.setValue(Optional.ofNullable(resObj.getAcStateData()).orElse(""));
        hexArea.setReadOnly(true);
        hexArea.setWidthFull();
        content.add(hexArea);

        if (decoded == null) {
            content.add(new Paragraph(t("controlTable.dialog.queryState.decodedUnavailable")));
            return content;
        }

        TextField summaryField = createReadOnlyField(t("controlTable.dialog.queryState.field.summary"), decoded.getSummary());
        TextField validField = createReadOnlyField(t("controlTable.dialog.queryState.field.valid"), String.valueOf(decoded.isValid()));
        TextField normalizedHexField = createReadOnlyField(t("controlTable.dialog.queryState.field.normalizedHex"), decoded.getNormalizedHex());
        TextField byteLengthField = createReadOnlyField(t("controlTable.dialog.queryState.field.byteLength"), decoded.getByteLength() != null ? String.valueOf(decoded.getByteLength()) : null);

        FormLayout metaLayout = new FormLayout(summaryField, validField, normalizedHexField, byteLengthField);
        metaLayout.setWidthFull();
        metaLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );
        metaLayout.setColspan(summaryField, 2);
        metaLayout.setColspan(normalizedHexField, 2);
        content.add(metaLayout);

        FormLayout decodedLayout = new FormLayout(
                createDecodedValueField(t("controlTable.dialog.queryState.field.power"), decoded.getPower()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.mode"), decoded.getMode()),
                createTemperatureField(t("controlTable.dialog.queryState.field.targetTemperature"), decoded.getTargetTemperature()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.fanMode"), decoded.getFanMode()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.swingMode"), decoded.getSwingMode()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.powerSelection"), decoded.getPowerSelection()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.meritB"), decoded.getMeritB()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.meritA"), decoded.getMeritA()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.airPureIon"), decoded.getAirPureIon()),
                createTemperatureField(t("controlTable.dialog.queryState.field.indoorTemperature"), decoded.getIndoorTemperature()),
                createTemperatureField(t("controlTable.dialog.queryState.field.outdoorTemperature"), decoded.getOutdoorTemperature()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.selfCleaning"), decoded.getSelfCleaning())
        );
        decodedLayout.setWidthFull();
        decodedLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("900px", 3)
        );
        content.add(decodedLayout);

        TextArea warningsArea = createReadOnlyTextArea(t("controlTable.dialog.queryState.field.warnings"), String.join("\n", decoded.getWarnings()));
        TextArea unknownFieldsArea = createReadOnlyTextArea(t("controlTable.dialog.queryState.field.unknownFields"), formatUnknownFields(decoded.getUnknownFields()));
        TextArea rawBytesArea = createReadOnlyTextArea(t("controlTable.dialog.queryState.field.rawBytes"), formatRawBytes(decoded.getRawBytes()));

        content.add(warningsArea, unknownFieldsArea, rawBytesArea);
        return content;
    }

    private TextField createDecodedValueField(String label, ToshibaAcStateDecodedResponse.DecodedValue value) {
        if (value == null) {
            return createReadOnlyField(label, "");
        }
        String text = value.getLabel();
        if (value.getCode() != null && !value.getCode().isBlank()) {
            text = (text == null || text.isBlank() ? "" : text + " | ") + value.getCode();
        }
        if (value.getRawHex() != null && !value.getRawHex().isBlank()) {
            text = (text == null || text.isBlank() ? "" : text + " | ") + value.getRawHex();
        }
        return createReadOnlyField(label, text);
    }

    private TextField createTemperatureField(String label, ToshibaAcStateDecodedResponse.TemperatureValue value) {
        if (value == null) {
            return createReadOnlyField(label, "");
        }
        String text = value.getLabel();
        if (value.getRawHex() != null && !value.getRawHex().isBlank()) {
            text = (text == null || text.isBlank() ? "" : text + " | ") + value.getRawHex();
        }
        return createReadOnlyField(label, text);
    }

    private TextField createReadOnlyField(String label, String value) {
        TextField field = new TextField(label);
        field.setReadOnly(true);
        field.setWidthFull();
        field.setValue(value != null ? value : "");
        return field;
    }

    private TextArea createReadOnlyTextArea(String label, String value) {
        TextArea area = new TextArea(label);
        area.setReadOnly(true);
        area.setWidthFull();
        area.setValue(value != null && !value.isBlank() ? value : "-");
        area.setMinHeight("120px");
        return area;
    }

    private String formatUnknownFields(List<ToshibaAcStateDecodedResponse.UnknownFieldValue> unknownFields) {
        if (unknownFields == null || unknownFields.isEmpty()) {
            return "";
        }
        return unknownFields.stream()
                .map(field -> String.format(
                        Locale.ROOT,
                        "%s: index=%d, raw=%s, unsigned=%s, signed=%s, note=%s",
                        field.getField(),
                        field.getIndex(),
                        field.getRawHex(),
                        field.getRawUnsigned(),
                        field.getRawSigned(),
                        field.getNote()
                ))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private String formatRawBytes(List<ToshibaAcStateDecodedResponse.RawByteValue> rawBytes) {
        if (rawBytes == null || rawBytes.isEmpty()) {
            return "";
        }
        return rawBytes.stream()
                .map(rawByte -> String.format(
                        Locale.ROOT,
                        "[%d] %s | unsigned=%s | signed=%s | %s",
                        rawByte.getIndex(),
                        rawByte.getRawHex(),
                        rawByte.getRawUnsigned(),
                        rawByte.getRawSigned(),
                        rawByte.getMeaning()
                ))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private String formatDecimal(BigDecimal value, String unit) {
        if (value == null) {
            return "-";
        }
        String number = value.stripTrailingZeros().toPlainString();
        return unit == null || unit.isBlank() ? number : number + " " + unit;
    }

    private String formatInstant(Instant instant) {
        return instant != null
                ? ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).format(formatter)
                : "-";
    }

    private void showWarning(String message) {
        Notification.show(message).addThemeVariants(NotificationVariant.LUMO_WARNING);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
