/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerRoomHeatSourceEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.ContractType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerConfigurationService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerActiveControlService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerMeasurementService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerPlanService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerThermalModelService;
import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService;
import com.nitramite.porssiohjain.services.nordpool.NordpoolMarket;
import com.nitramite.porssiohjain.views.components.HeatingPlanChart;
import com.nitramite.porssiohjain.views.components.SiteWeatherForecastChart;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@PageTitle("Pörssiohjain - Heating Planner")
@Route("heating-planner")
@PermitAll
public class HeatingPlannerView extends VerticalLayout implements BeforeEnterObserver {

    private static final ZoneId ZONE = ZoneId.of("Europe/Helsinki");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final AuthService authService;
    private final I18nService i18n;
    private final SiteWeatherRepository siteWeatherRepository;
    private final NordpoolRepository nordpoolRepository;

    public HeatingPlannerView(AuthService authService, I18nService i18n,
                              HeatingPlanSimulationService simulationService,
                              SiteRepository siteRepository, SiteWeatherRepository siteWeatherRepository,
                              DeviceRepository deviceRepository,
                              NordpoolRepository nordpoolRepository,
                              ElectricityContractRepository contractRepository,
                              HeatingPlannerConfigurationService configurationService,
                              HeatingPlannerMeasurementService measurementService,
                              HeatingPlannerThermalModelService thermalModelService,
                              HeatingPlannerActiveControlService activeControlService,
                              HeatingPlannerPlanService planService,
                              ZigbeeDeviceMeasurementRepository measurementRepository) {
        this.authService = authService;
        this.i18n = i18n;
        this.siteWeatherRepository = siteWeatherRepository;
        this.nordpoolRepository = nordpoolRepository;
        setSizeFull();
        setAlignItems(Alignment.CENTER);

        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        List<SiteEntity> sites = account == null ? List.of() : siteRepository.findByAccountId(account.getId());
        List<DeviceEntity> thermostats = account == null ? List.of() : deviceRepository.findByAccountIdOrderByIdAsc(account.getId()).stream()
                .filter(device -> device.getDeviceType() == DeviceType.THERMOSTAT)
                .toList();
        List<DeviceEntity> temperatureSensors = account == null ? List.of() : deviceRepository.findByAccountIdOrderByIdAsc(account.getId()).stream()
                .filter(device -> device.getDeviceType() == DeviceType.TEMPERATURE_SENSOR)
                .toList();
        List<DeviceEntity> floorSensors = java.util.stream.Stream.concat(thermostats.stream(), temperatureSensors.stream())
                .distinct().toList();
        List<ElectricityContractEntity> transferContracts = account == null ? List.of()
                : contractRepository.findByAccountId(account.getId()).stream()
                .filter(contract -> contract.getType() == ContractType.TRANSFER)
                .toList();
        List<RoomOverview> roomRows = new ArrayList<>();
        AtomicBoolean loadingConfiguration = new AtomicBoolean(false);

        VerticalLayout card = new VerticalLayout();
        card.addClassName("responsive-card");
        card.setWidthFull();
        card.setMaxWidth("none");
        card.setAlignItems(Alignment.STRETCH);

        Button back = new Button("Back", VaadinIcon.ARROW_LEFT.create(), e -> getUI()
                .ifPresent(ui -> ui.navigate(HomeView.class)));
        H1 title = new H1("Heating Planner");
        title.getStyle().set("margin", "0");
        Checkbox plannerEnabled = new Checkbox("Enabled", false);
        plannerEnabled.setHelperText("Disabling keeps the room configuration but prevents planner use.");
        HorizontalLayout heading = new HorizontalLayout(title, plannerEnabled);
        heading.setAlignItems(Alignment.CENTER);

        Paragraph summary = new Paragraph("Whole-house plan · charge floor heating when electricity is cheap and recommend wood burning before expensive periods");

        ComboBox<SiteEntity> siteSelect = new ComboBox<>("Site");
        siteSelect.setItems(sites);
        siteSelect.setItemLabelGenerator(site -> site.getName() + " · " + site.getTimezone());
        siteSelect.setWidthFull();
        siteSelect.setHelperText("Weather forecast comes from this site's configured weather place.");
        preferredSite(sites, account == null ? Optional.empty() : configurationService.preferredSiteId(account.getId()))
                .ifPresentOrElse(siteSelect::setValue, () -> sites.stream().findFirst().ifPresent(siteSelect::setValue));
        NumberField taxPercent = numberField("Market VAT (%)", 25.5, 0, 100);
        ComboBox<ElectricityContractEntity> transferContract = new ComboBox<>("Transfer contract");
        transferContract.setItems(transferContracts);
        transferContract.setItemLabelGenerator(ElectricityContractEntity::getName);
        transferContract.setClearButtonVisible(true);
        transferContract.setWidthFull();
        transferContract.setHelperText("Added to taxed Nordpool prices when calculating the plan.");

        Span siteWeatherStatus = new Span();
        Button configureSiteWeather = new Button("Set weather place in Sites", VaadinIcon.MAP_MARKER.create(),
                event -> getUI().ifPresent(ui -> ui.navigate(SitesView.class)));
        configureSiteWeather.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        VerticalLayout siteWarnings = new VerticalLayout(siteWeatherStatus, configureSiteWeather);
        siteWarnings.setPadding(false);
        siteWarnings.setSpacing(false);
        FormLayout siteForm = new FormLayout(siteSelect, taxPercent, transferContract, siteWarnings);
        siteForm.setWidthFull();
        siteForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("650px", 2));
        VerticalLayout siteConfigurationContent = new VerticalLayout();
        siteConfigurationContent.setPadding(false);
        siteConfigurationContent.setWidthFull();
        siteConfigurationContent.setAlignItems(Alignment.STRETCH);
        VerticalLayout weatherForecastChartHost = new VerticalLayout();
        weatherForecastChartHost.setPadding(false);
        weatherForecastChartHost.setWidthFull();
        siteConfigurationContent.add(siteForm, weatherForecastChartHost);
        Details siteConfiguration = new Details("Site and weather forecast", siteConfigurationContent);
        siteConfiguration.setWidthFull();
        siteConfiguration.setOpened(false);

        Checkbox loaded = new Checkbox("Stove is loaded and ready", true);
        TimePicker availableFrom = new TimePicker("Available to light from", LocalTime.of(6, 0));
        TimePicker availableTo = new TimePicker("Available to light until", LocalTime.of(22, 0));
        NumberField woodAmount = numberField("Static wood load (kg)", 8, 1, 30);
        NumberField releaseDelay = numberField("Delay before useful heat (hours)", 0.75, 0, 12);
        NumberField releaseDuration = numberField("Heat release duration (hours)", 6, 0.25, 48);
        NumberField plannerWeatherThreshold = numberField("Planner active below (°C)", 5, -40, 40);
        NumberField woodWeatherThreshold = numberField("Recommend wood below (°C)", 0, -40, 40);
        FormLayout stoveForm = new FormLayout(loaded, availableFrom, availableTo);
        stoveForm.setWidthFull();
        stoveForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("650px", 3));
        Details stoveConfiguration = new Details("Wood stove availability", stoveForm);
        stoveConfiguration.setWidthFull();
        stoveConfiguration.setOpened(false);
        FormLayout stoveHeatProfileForm = new FormLayout(woodAmount, releaseDelay, releaseDuration,
                plannerWeatherThreshold, woodWeatherThreshold);
        stoveHeatProfileForm.setWidthFull();
        stoveHeatProfileForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("650px", 3));
        Details stoveHeatProfileConfiguration = new Details("Wood stove heat profile", stoveHeatProfileForm);
        stoveHeatProfileConfiguration.setWidthFull();
        stoveHeatProfileConfiguration.setOpened(false);

        Grid<RoomOverview> rooms = roomOverviewGrid(roomRows, thermostats, temperatureSensors, floorSensors);
        Button addRoom = new Button("Add room", VaadinIcon.PLUS.create(), event -> {
            roomRows.add(new RoomOverview("New room", HeatingPlannerHeatSourceType.FLOOR_HEATING, new BigDecimal("21.00"),
                    new BigDecimal("23.00"), new BigDecimal("27.00"), new BigDecimal("29.00"),
                    new BigDecimal("19.00"), null, null, null));
            rooms.getDataProvider().refreshAll();
        });
        addRoom.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        VerticalLayout roomConfigurationContent = new VerticalLayout(rooms, addRoom);
        roomConfigurationContent.setPadding(false);
        roomConfigurationContent.setWidthFull();
        roomConfigurationContent.setAlignItems(Alignment.STRETCH);
        Details roomConfiguration = new Details("Rooms and heat sources", roomConfigurationContent);
        roomConfiguration.setWidthFull();
        roomConfiguration.setOpened(false);
        Details recentMeasurements = recentMeasurementsDetails(account == null ? null : account.getId(), measurementRepository);

        VerticalLayout planHost = new VerticalLayout();
        planHost.setPadding(false);
        planHost.setWidthFull();
        Span activeControlStatus = new Span();
        Button enableActiveControl = new Button("Enable active thermostat control", VaadinIcon.POWER_OFF.create());
        Button disableActiveControl = new Button("Disable active control", VaadinIcon.CLOSE_CIRCLE.create());
        enableActiveControl.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        disableActiveControl.addThemeVariants(ButtonVariant.LUMO_ERROR);
        HorizontalLayout activeControlActions = new HorizontalLayout(enableActiveControl, disableActiveControl);
        activeControlActions.setWrap(true);
        VerticalLayout activeControlPanel = new VerticalLayout(new H3("Active thermostat control"),
                activeControlStatus, activeControlActions);
        activeControlPanel.setPadding(false);
        activeControlPanel.setSpacing(false);
        Runnable calculate = () -> {
            planHost.removeAll();
            SiteEntity selectedSite = siteSelect.getValue();
            List<SiteWeatherEntity> forecast = forecastForHorizon(selectedSite);
            MarketSeries marketSeries = marketSeries(account, selectedSite, decimalOrDefault(taxPercent.getValue(), "25.50"),
                    transferContract.getValue(), forecast);
            HeatingPlanSimulationService.PriceThresholds priceThresholds =
                    simulationService.calculateDynamicPriceThresholds(marketSeries.points());
            Instant calculationTime = Instant.now();
            List<RoomPlan> roomPlans = roomRows.stream()
                    .map(room -> {
                        MeasurementInputs measurements = measurementInputs(room, measurementService, calculationTime);
                        try {
                            HeatingPlanSimulationService.ThermalModel model = defaultThermalModel();
                            String modelEvidence = "configured conservative thermal defaults";
                            if (account != null && selectedSite != null) {
                                var resolution = thermalModelService.learnAndResolve(account.getId(), selectedSite.getId(),
                                        room.room(), model, calculationTime);
                                model = resolution.model();
                                modelEvidence = resolution.learned()
                                        ? "observed cooling model, " + resolution.sampleCount() + " samples, confidence "
                                        + resolution.confidence().movePointRight(2).setScale(0, RoundingMode.HALF_UP) + "%"
                                        : resolution.reason();
                            }
                            modelEvidence = thermalModelEvidence(modelEvidence, model);
                            return new RoomPlan(room.room(), room.heatSource(), room.controller(),
                                    measurements.roomTemperature(), measurements.floorTemperature(),
                                    measurements.roomMeasurement(), measurements.floorMeasurement(),
                                    room.targetRoomTemperature(), room.normalFloorTemperature(),
                                    room.maximumPreheatFloorTemperature(), room.absoluteMaximumFloorTemperature(),
                                    room.dischargeFloorSetpoint(), modelEvidence,
                                    simulationService.simulate(simulationRequest(
                                            loaded.getValue(), availableFrom.getValue(), availableTo.getValue(),
                                            woodAmount.getValue(), releaseDelay.getValue(), releaseDuration.getValue(),
                                            plannerWeatherThreshold.getValue(), woodWeatherThreshold.getValue(),
                                            measurements.floorTemperature(), measurements.roomTemperature(),
                                            room.targetRoomTemperature(), room.normalFloorTemperature(),
                                            room.maximumPreheatFloorTemperature(), room.absoluteMaximumFloorTemperature(),
                                            room.dischargeFloorSetpoint(), marketSeries.points(), model,
                                            measurements.roomMeasurement().fresh(), measurements.floorMeasurement().fresh(),
                                            priceThresholds)),
                                    null);
                        } catch (IllegalArgumentException ex) {
                            return new RoomPlan(room.room(), room.heatSource(), room.controller(),
                                    measurements.roomTemperature(), measurements.floorTemperature(),
                                    measurements.roomMeasurement(), measurements.floorMeasurement(),
                                    room.targetRoomTemperature(), room.normalFloorTemperature(),
                                    room.maximumPreheatFloorTemperature(), room.absoluteMaximumFloorTemperature(),
                                    room.dischargeFloorSetpoint(), "Model unavailable because plan inputs are invalid",
                                    null, ex.getMessage());
                        }
                    })
                    .toList();
            if (roomPlans.isEmpty()) {
                roomPlans = List.of(new RoomPlan("Unconfigured house", HeatingPlannerHeatSourceType.OTHER, null,
                        new BigDecimal("21.00"), new BigDecimal("22.00"),
                        HeatingPlannerMeasurementService.LatestMeasurement.missing(),
                        HeatingPlannerMeasurementService.LatestMeasurement.missing(),
                        new BigDecimal("21.00"), new BigDecimal("23.00"), new BigDecimal("27.00"),
                        new BigDecimal("29.00"), new BigDecimal("19.00"),
                        thermalModelEvidence("Configured fallback thermal model", defaultThermalModel()),
                        simulationService.simulate(simulationRequest(
                                loaded.getValue(), availableFrom.getValue(), availableTo.getValue(),
                                woodAmount.getValue(), releaseDelay.getValue(), releaseDuration.getValue(),
                                plannerWeatherThreshold.getValue(), woodWeatherThreshold.getValue(),
                                new BigDecimal("22.00"), new BigDecimal("21.00"),
                                new BigDecimal("21.00"), new BigDecimal("23.00"), new BigDecimal("27.00"),
                                new BigDecimal("29.00"), new BigDecimal("19.00"), marketSeries.points(),
                                defaultThermalModel(), false, false, priceThresholds)),
                        null));
            }
            if (account != null && selectedSite != null) {
                Map<String, HeatingPlanSimulationService.SimulationResult> resultsByRoom = new LinkedHashMap<>();
                roomPlans.stream()
                        .filter(roomPlan -> roomPlan.result() != null)
                        .forEach(roomPlan -> resultsByRoom.put(roomPlan.room(), roomPlan.result()));
                if (!resultsByRoom.isEmpty()) {
                    boolean persisted = planService.persistSimulatedPlan(
                            account.getId(), selectedSite.getId(), resultsByRoom);
                    if (persisted) {
                        try {
                            activeControlService.activateLatestRecalculatedPlanIfOptedIn(
                                    account.getId(), selectedSite.getId(), calculationTime);
                        } catch (IllegalStateException ex) {
                            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_WARNING);
                        }
                    }
                }
            }
            PlanEvidenceInputs evidenceInputs = new PlanEvidenceInputs(calculationTime,
                    plannerWeatherThreshold.getValue(), woodWeatherThreshold.getValue(), loaded.getValue(),
                    availableFrom.getValue(), availableTo.getValue(), woodAmount.getValue(),
                    releaseDelay.getValue(), releaseDuration.getValue(), priceThresholds.cheapPriceThreshold(),
                    priceThresholds.expensivePriceThreshold());
            planHost.add(planContent(roomPlans, selectedSite, forecast, marketSeries, evidenceInputs));
            refreshActiveControlState(activeControlService, account == null ? null : account.getId(), selectedSite,
                    activeControlStatus, enableActiveControl, disableActiveControl);
        };
        Button recalculate = new Button("Recalculate plan", VaadinIcon.REFRESH.create(), event -> calculate.run());
        recalculate.setWidthFull();
        recalculate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        enableActiveControl.addClickListener(event -> {
            SiteEntity selectedSite = siteSelect.getValue();
            if (account == null || selectedSite == null) return;
            var readiness = activeControlService.readiness(account.getId(), selectedSite.getId(), Instant.now());
            if (!readiness.ready()) {
                showActiveControlIssues(readiness.issues());
                return;
            }
            Dialog confirmation = new Dialog();
            confirmation.setHeaderTitle("Enable active thermostat control?");
            VerticalLayout content = new VerticalLayout(
                    new Paragraph("Heating Planner will send the active plan's floor setpoints to the selected Zigbee thermostats."),
                    new Span("Plan: " + readiness.candidatePlanVersion()),
                    new Span(readiness.issues().isEmpty()
                            ? "Fresh room and floor sensors, learned-model confidence, gateway state, and plan coverage have been verified."
                            : "Only rooms passing sensor, model, gateway, and plan checks will be controlled. Excluded rooms remain on fallback: "
                            + String.join("; ", readiness.issues())));
            content.setPadding(false);
            Button confirm = new Button("Enable control", click -> {
                try {
                    activeControlService.activate(account.getId(), selectedSite.getId(), Instant.now());
                    confirmation.close();
                    Notification.show("Active thermostat control enabled")
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    refreshActiveControlState(activeControlService, account.getId(), selectedSite,
                            activeControlStatus, enableActiveControl, disableActiveControl);
                } catch (IllegalStateException ex) {
                    confirmation.close();
                    Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            confirmation.add(content);
            confirmation.getFooter().add(new Button("Cancel", click -> confirmation.close()), confirm);
            confirmation.open();
        });
        disableActiveControl.addClickListener(event -> {
            SiteEntity selectedSite = siteSelect.getValue();
            if (account == null || selectedSite == null) return;
            activeControlService.disable(account.getId(), selectedSite.getId(), Instant.now());
            Notification.show("Active control disabled; Heating Planner commands were expired")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshActiveControlState(activeControlService, account.getId(), selectedSite,
                    activeControlStatus, enableActiveControl, disableActiveControl);
        });
        Button saveConfiguration = new Button("Save rooms", VaadinIcon.CHECK.create(), event -> {
            SiteEntity selectedSite = siteSelect.getValue();
            if (account == null || selectedSite == null) {
                Notification.show("Select a site before saving").addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            try {
                configurationService.save(account.getId(), selectedSite.getId(),
                        new HeatingPlannerConfigurationService.SettingsConfiguration(
                                plannerEnabled.getValue(),
                                decimalOrDefault(plannerWeatherThreshold.getValue(), "5.00"),
                                decimalOrDefault(woodWeatherThreshold.getValue(), "0.00"),
                                decimalOrDefault(taxPercent.getValue(), "25.50"),
                                transferContract.getValue() == null ? null : transferContract.getValue().getId(),
                                loaded.getValue(),
                                availableFrom.getValue(),
                                availableTo.getValue(),
                                decimalOrDefault(woodAmount.getValue(), "8.00"),
                                minutesFromHours(releaseDelay.getValue(), 45),
                                minutesFromHours(releaseDuration.getValue(), 360)
                        ),
                        roomRows.stream()
                                .map(row -> new HeatingPlannerConfigurationService.RoomConfiguration(
                                        row.room(), row.heatSource(), row.targetRoomTemperature(),
                                        row.normalFloorTemperature(), row.maximumPreheatFloorTemperature(),
                                        row.absoluteMaximumFloorTemperature(), row.dischargeFloorSetpoint(),
                                        row.controller() == null ? null : row.controller().getId(),
                                        row.roomSensor() == null ? null : row.roomSensor().getId(),
                                        row.floorSensor() == null ? null : row.floorSensor().getId()
                                ))
                                .toList());
                Notification.show("Heating Planner rooms saved").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                calculate.run();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveConfiguration.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
        roomConfigurationContent.add(saveConfiguration);
        plannerEnabled.addValueChangeListener(event -> {
            if (loadingConfiguration.get()) {
                return;
            }
            SiteEntity selectedSite = siteSelect.getValue();
            if (account == null || selectedSite == null) {
                Notification.show("Select a site before changing Heating Planner status")
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            try {
                savePlannerSettings(configurationService, account.getId(), selectedSite, plannerEnabled,
                        plannerWeatherThreshold, woodWeatherThreshold, taxPercent, transferContract,
                        loaded, availableFrom, availableTo, woodAmount, releaseDelay, releaseDuration);
                if (!event.getValue()) {
                    activeControlService.disable(account.getId(), selectedSite.getId(), Instant.now());
                }
                Notification.show(event.getValue() ? "Heating Planner enabled" : "Heating Planner disabled")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                refreshActiveControlState(activeControlService, account.getId(), selectedSite,
                        activeControlStatus, enableActiveControl, disableActiveControl);
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
                loadingConfiguration.set(true);
                plannerEnabled.setValue(event.getOldValue());
                loadingConfiguration.set(false);
            }
        });
        plannerWeatherThreshold.addValueChangeListener(event -> {
            if (!loadingConfiguration.get()) {
                savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                        siteSelect.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                        taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                        releaseDelay, releaseDuration);
                calculate.run();
            }
        });
        woodWeatherThreshold.addValueChangeListener(event -> {
            if (!loadingConfiguration.get()) {
                savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                        siteSelect.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                        taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                        releaseDelay, releaseDuration);
                calculate.run();
            }
        });
        taxPercent.addValueChangeListener(event -> {
            if (!loadingConfiguration.get()) {
                savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                        siteSelect.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                        taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                        releaseDelay, releaseDuration);
                calculate.run();
            }
        });
        transferContract.addValueChangeListener(event -> {
            if (!loadingConfiguration.get()) {
                savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                        siteSelect.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                        taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                        releaseDelay, releaseDuration);
                calculate.run();
            }
        });
        loaded.addValueChangeListener(event -> {
            if (!loadingConfiguration.get()) {
                savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                        siteSelect.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                        taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                        releaseDelay, releaseDuration);
                calculate.run();
            }
        });
        availableFrom.addValueChangeListener(event -> {
            if (!loadingConfiguration.get()) {
                savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                        siteSelect.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                        taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                        releaseDelay, releaseDuration);
                calculate.run();
            }
        });
        availableTo.addValueChangeListener(event -> {
            if (!loadingConfiguration.get()) {
                savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                        siteSelect.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                        taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                        releaseDelay, releaseDuration);
                calculate.run();
            }
        });
        woodAmount.addValueChangeListener(event -> {
            if (!loadingConfiguration.get()) {
                savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                        siteSelect.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                        taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                        releaseDelay, releaseDuration);
                calculate.run();
            }
        });
        releaseDelay.addValueChangeListener(event -> {
            if (!loadingConfiguration.get()) {
                savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                        siteSelect.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                        taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                        releaseDelay, releaseDuration);
                calculate.run();
            }
        });
        releaseDuration.addValueChangeListener(event -> {
            if (!loadingConfiguration.get()) {
                savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                        siteSelect.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                        taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                        releaseDelay, releaseDuration);
                calculate.run();
            }
        });
        siteSelect.addValueChangeListener(event -> {
            updateSiteWeatherStatus(siteWeatherStatus, configureSiteWeather, event.getValue());
            updateWeatherForecastChart(weatherForecastChartHost, event.getValue());
            loadConfiguration(configurationService, account == null ? null : account.getId(), event.getValue(),
                    loadingConfiguration, plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                    taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                    releaseDelay, releaseDuration, roomRows, rooms, thermostats, temperatureSensors, floorSensors,
                    transferContracts);
            savePlannerSettingsSilently(configurationService, account == null ? null : account.getId(),
                    event.getValue(), plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                    taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                    releaseDelay, releaseDuration);
            calculate.run();
        });
        updateSiteWeatherStatus(siteWeatherStatus, configureSiteWeather, siteSelect.getValue());
        updateWeatherForecastChart(weatherForecastChartHost, siteSelect.getValue());
        loadConfiguration(configurationService, account == null ? null : account.getId(), siteSelect.getValue(),
                loadingConfiguration, plannerEnabled, plannerWeatherThreshold, woodWeatherThreshold,
                taxPercent, transferContract, loaded, availableFrom, availableTo, woodAmount,
                releaseDelay, releaseDuration, roomRows, rooms, thermostats, temperatureSensors, floorSensors,
                transferContracts);
        calculate.run();

        card.add(back, heading, summary, activeControlPanel, siteConfiguration, roomConfiguration, recentMeasurements, stoveConfiguration,
                stoveHeatProfileConfiguration, recalculate, planHost);
        add(card);
    }

    private Details recentMeasurementsDetails(Long accountId, ZigbeeDeviceMeasurementRepository measurementRepository) {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setWidthFull();
        content.setAlignItems(Alignment.STRETCH);

        Span status = new Span();
        Grid<RecentMeasurementRow> grid = new Grid<>(RecentMeasurementRow.class, false);
        grid.setWidthFull();
        grid.addColumn(RecentMeasurementRow::measuredAt)
                .setHeader("Measured")
                .setWidth("170px")
                .setFlexGrow(0);
        grid.addColumn(RecentMeasurementRow::receivedAt)
                .setHeader("Received")
                .setWidth("170px")
                .setFlexGrow(0);
        grid.addComponentColumn(row -> wrappingCell(row.device()))
                .setHeader("Device")
                .setFlexGrow(2);
        grid.addColumn(RecentMeasurementRow::zigbeeIeee)
                .setHeader("IEEE")
                .setWidth("160px")
                .setFlexGrow(0);
        grid.addColumn(RecentMeasurementRow::profile)
                .setHeader("Profile")
                .setFlexGrow(1);
        grid.addColumn(RecentMeasurementRow::type)
                .setHeader("Type")
                .setWidth("170px")
                .setFlexGrow(0);
        grid.addColumn(RecentMeasurementRow::value)
                .setHeader("Value")
                .setWidth("110px")
                .setFlexGrow(0);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setAllRowsVisible(true);
        grid.addItemClickListener(event -> openMeasurementHistoryDialog(
                accountId, event.getItem(), measurementRepository));

        Runnable refresh = () -> {
            if (accountId == null) {
                grid.setItems(List.of());
                status.setText("Sign in to see received Zigbee sensor readings.");
                return;
            }
            Instant after = Instant.now().minus(Duration.ofHours(12));
            List<RecentMeasurementRow> rows = measurementRepository
                    .findLatestDistinctMeasurements(accountId, after)
                    .stream()
                    .map(this::recentMeasurementRow)
                    .toList();
            grid.setItems(rows);
            status.setText("Showing the latest value for " + rows.size()
                    + " distinct IEEE address and measurement type combinations from the past 12 hours. Click a row for history.");
        };
        Button refreshButton = new Button("Refresh readings", VaadinIcon.REFRESH.create(), event -> refresh.run());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refresh.run();

        content.add(status, refreshButton, grid);
        Details details = new Details("Recent Zigbee sensor readings", content);
        details.setWidthFull();
        details.setOpened(false);
        return details;
    }

    private RecentMeasurementRow recentMeasurementRow(ZigbeeDeviceMeasurementEntity measurement) {
        DeviceEntity device = measurement.getDevice();
        String deviceName = device == null ? "-" : deviceLabel(device);
        return new RecentMeasurementRow(
                formatInstant(measurement.getMeasuredAt()),
                formatInstant(measurement.getReceivedAt()),
                deviceName,
                measurement.getZigbeeIeee(),
                measurement.getProfile(),
                measurement.getMeasurementType() + " · " + measurement.getMeasurementKey(),
                measurement.getValue().stripTrailingZeros().toPlainString(),
                measurement.getMeasurementType(),
                measurement.getMeasurementKey()
        );
    }

    private void openMeasurementHistoryDialog(Long accountId, RecentMeasurementRow selected,
                                              ZigbeeDeviceMeasurementRepository measurementRepository) {
        if (accountId == null || selected == null) {
            return;
        }
        List<RecentMeasurementRow> history = measurementRepository
                .findTop500ByAccountIdAndZigbeeIeeeAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                        accountId, selected.zigbeeIeee(), selected.measurementType(), selected.measurementKey())
                .stream()
                .map(this::recentMeasurementRow)
                .toList();
        Grid<RecentMeasurementRow> historyGrid = new Grid<>(RecentMeasurementRow.class, false);
        historyGrid.addColumn(RecentMeasurementRow::measuredAt).setHeader("Measured").setFlexGrow(1);
        historyGrid.addColumn(RecentMeasurementRow::receivedAt).setHeader("Received").setFlexGrow(1);
        historyGrid.addColumn(RecentMeasurementRow::value).setHeader("Value").setWidth("120px").setFlexGrow(0);
        historyGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        historyGrid.setItems(history);
        historyGrid.setHeight("60vh");

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(selected.zigbeeIeee() + " · " + selected.type());
        dialog.setWidth("min(900px, 95vw)");
        dialog.add(new Paragraph("Newest " + history.size() + " history values (limited to 500)."), historyGrid);
        dialog.getFooter().add(new Button("Close", event -> dialog.close()));
        dialog.open();
    }

    private VerticalLayout planContent(List<RoomPlan> roomPlans, SiteEntity site,
                                       List<SiteWeatherEntity> forecast, MarketSeries marketSeries,
                                       PlanEvidenceInputs inputs) {
        VerticalLayout plan = new VerticalLayout();
        plan.setPadding(false);
        Details evidence = new Details("Inputs used to determine this plan",
                evidenceContent(roomPlans, site, forecast, marketSeries, inputs));
        evidence.setOpened(false);
        Details calculationBreakdown = roomCalculationBreakdown(roomPlans);
        LocalDate today = LocalDate.now(ZONE);
        VerticalLayout todayContent = dayContent(roomPlans, today, true);
        VerticalLayout tomorrowContent = dayContent(roomPlans, today.plusDays(1), false);
        Tab todayTab = new Tab("Today");
        Tab tomorrowTab = new Tab("Tomorrow");
        Tabs tabs = new Tabs(todayTab, tomorrowTab);
        tabs.setWidthFull();
        tomorrowContent.setVisible(false);
        tabs.addSelectedChangeListener(event -> {
            boolean showToday = event.getSelectedTab() == todayTab;
            todayContent.setVisible(showToday);
            tomorrowContent.setVisible(!showToday);
        });
        plan.add(evidence, calculationBreakdown, tabs, todayContent, tomorrowContent);
        return plan;
    }

    private NumberField numberField(String label, double value, double min, double max) {
        NumberField field = new NumberField(label);
        field.setValue(value);
        field.setMin(min);
        field.setMax(max);
        field.setStep(0.25);
        return field;
    }

    private void refreshActiveControlState(HeatingPlannerActiveControlService service, Long accountId, SiteEntity site,
                                           Span status, Button enable, Button disable) {
        if (accountId == null || site == null) {
            status.setText("Select a site to check active-control readiness.");
            enable.setEnabled(false);
            disable.setEnabled(false);
            return;
        }
        var readiness = service.readiness(accountId, site.getId(), Instant.now());
        if (readiness.active()) {
            String excludedRooms = readiness.issues().stream()
                    .filter(issue -> issue.contains(":"))
                    .collect(java.util.stream.Collectors.joining("; "));
            status.setText("ACTIVE — automatic recalculation runs every 15 minutes"
                    + (readiness.lastAutomaticActivationAt() == null ? ""
                    : "; last automatic activation " + formatInstant(readiness.lastAutomaticActivationAt()))
                    + (readiness.lastAutomationError() == null ? ""
                    : "; latest automation warning: " + readiness.lastAutomationError())
                    + (excludedRooms.isEmpty() ? "" : "; excluded rooms remain on fallback: " + excludedRooms));
            status.getElement().getThemeList().add("badge success");
        } else if (readiness.ready()) {
            status.setText(readiness.issues().isEmpty()
                    ? "Ready — all activation checks pass. Review and explicitly enable control when desired."
                    : "Partially ready — ready rooms can use active control; excluded rooms remain on fallback. "
                    + String.join("; ", readiness.issues()));
            status.getElement().getThemeList().add("badge contrast");
        } else {
            status.setText("Not ready — " + String.join("; ", readiness.issues()));
            status.getElement().getThemeList().add("badge warning");
        }
        enable.setText(readiness.active() ? "Activate latest recalculated plan" : "Enable active thermostat control");
        enable.setEnabled(readiness.ready());
        disable.setEnabled(readiness.active());
    }

    private void showActiveControlIssues(List<String> issues) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Active control is not ready");
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        issues.forEach(issue -> content.add(new Span("• " + issue)));
        dialog.add(content);
        dialog.getFooter().add(new Button("Close", event -> dialog.close()));
        dialog.open();
    }

    private Grid<RoomOverview> roomOverviewGrid(List<RoomOverview> roomRows, List<DeviceEntity> thermostats,
                                                List<DeviceEntity> temperatureSensors,
                                                List<DeviceEntity> floorSensors) {
        Grid<RoomOverview> grid = new Grid<>(RoomOverview.class, false);
        grid.setWidthFull();
        grid.addComponentColumn(row -> {
            com.vaadin.flow.component.textfield.TextField room = new com.vaadin.flow.component.textfield.TextField();
            room.setValue(row.room());
            room.setWidthFull();
            room.addValueChangeListener(event -> row.setRoom(event.getValue()));
            return room;
        }).setHeader("Room").setFlexGrow(1);
        grid.addComponentColumn(row -> {
            ComboBox<HeatingPlannerHeatSourceType> heatSource = new ComboBox<>();
            heatSource.setItems(HeatingPlannerHeatSourceType.values());
            heatSource.setItemLabelGenerator(HeatingPlannerHeatSourceType::label);
            heatSource.setValue(row.heatSource());
            heatSource.setWidthFull();
            heatSource.addValueChangeListener(event -> row.setHeatSource(event.getValue()));
            return heatSource;
        }).setHeader("Heat source").setFlexGrow(1);
        grid.addComponentColumn(row -> {
            NumberField target = new NumberField();
            target.setValue(row.targetRoomTemperature().doubleValue());
            target.setMin(5);
            target.setMax(35);
            target.setStep(0.25);
            target.setSuffixComponent(new Span("°C"));
            target.setWidthFull();
            target.addValueChangeListener(event -> {
                if (event.getValue() != null) {
                    row.setTargetRoomTemperature(BigDecimal.valueOf(event.getValue()));
                }
            });
            return target;
        }).setHeader("Comfort target").setFlexGrow(1);
        grid.addComponentColumn(row -> roomTemperatureField(row.maximumPreheatFloorTemperature(), 5, 40,
                row::setMaximumPreheatFloorTemperature))
                .setHeader("Preheat max").setFlexGrow(1);
        grid.addComponentColumn(row -> {
            ComboBox<DeviceEntity> controller = new ComboBox<>();
            controller.setItems(thermostats);
            controller.setItemLabelGenerator(this::deviceLabel);
            controller.setValue(row.controller());
            controller.setPlaceholder("No controller");
            controller.setClearButtonVisible(true);
            controller.setWidthFull();
            controller.addValueChangeListener(event -> row.setController(event.getValue()));
            return controller;
        }).setHeader("Controlling device").setFlexGrow(2);
        grid.addComponentColumn(row -> {
            ComboBox<DeviceEntity> sensor = new ComboBox<>();
            sensor.setItems(temperatureSensors);
            sensor.setItemLabelGenerator(this::deviceLabel);
            sensor.setValue(row.roomSensor());
            sensor.setPlaceholder("No room sensor");
            sensor.setClearButtonVisible(true);
            sensor.setWidthFull();
            sensor.addValueChangeListener(event -> row.setRoomSensor(event.getValue()));
            return sensor;
        }).setHeader("Room sensor").setFlexGrow(2);
        grid.addComponentColumn(row -> {
            ComboBox<DeviceEntity> sensor = new ComboBox<>();
            sensor.setItems(floorSensors);
            sensor.setItemLabelGenerator(this::deviceLabel);
            sensor.setValue(row.floorSensor());
            sensor.setPlaceholder("Required for preheating");
            sensor.setClearButtonVisible(true);
            sensor.setWidthFull();
            sensor.addValueChangeListener(event -> row.setFloorSensor(event.getValue()));
            return sensor;
        }).setHeader("Floor sensor").setFlexGrow(2);
        grid.addComponentColumn(row -> {
            Button delete = new Button(VaadinIcon.TRASH.create(), event -> {
                roomRows.remove(row);
                grid.getDataProvider().refreshAll();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
            delete.getElement().setAttribute("aria-label", "Delete room");
            return delete;
        }).setHeader("").setWidth("70px").setFlexGrow(0);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setAllRowsVisible(true);
        grid.setItems(roomRows);
        return grid;
    }

    private NumberField roomTemperatureField(BigDecimal value, double min, double max,
                                             java.util.function.Consumer<BigDecimal> valueConsumer) {
        NumberField field = new NumberField();
        field.setValue(value.doubleValue());
        field.setMin(min);
        field.setMax(max);
        field.setStep(0.25);
        field.setSuffixComponent(new Span("°C"));
        field.setWidthFull();
        field.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                valueConsumer.accept(BigDecimal.valueOf(event.getValue()));
            }
        });
        return field;
    }

    private VerticalLayout dayContent(List<RoomPlan> roomPlans, LocalDate date, boolean today) {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        List<RoomDayPlan> plans = roomPlans.stream()
                .filter(plan -> plan.result() != null)
                .map(plan -> new RoomDayPlan(plan, plan.result().points().stream()
                        .filter(point -> point.time().atZone(ZONE).toLocalDate().equals(date))
                        .toList()))
                .filter(plan -> !plan.points().isEmpty())
                .toList();
        List<RoomPlan> invalidPlans = roomPlans.stream()
                .filter(plan -> plan.result() == null)
                .toList();
        invalidPlans.forEach(plan -> content.add(new Paragraph(plan.room() + ": " + plan.planError())));
        if (plans.isEmpty()) {
            content.add(new Paragraph(invalidPlans.isEmpty() ? "Plan unavailable." : "Fix room settings and recalculate."));
            return content;
        }
        ComboBox<RoomDayPlan> roomFilter = new ComboBox<>("Room plan");
        roomFilter.setItems(plans);
        roomFilter.setItemLabelGenerator(plan -> plan.roomPlan().room());
        roomFilter.setValue(plans.getFirst());
        roomFilter.setWidthFull();
        VerticalLayout selectedRoomPlan = new VerticalLayout();
        selectedRoomPlan.setPadding(false);
        selectedRoomPlan.setWidthFull();
        Runnable renderSelectedRoom = () -> {
            selectedRoomPlan.removeAll();
            RoomDayPlan selected = roomFilter.getValue();
            if (selected != null) {
                selectedRoomPlan.add(new HeatingPlanChart(selected.points(), ZONE,
                                i18n.t("heatingPlanner.chart.now"), Instant.now()),
                        plannedActions(selected.points(), selected.roomPlan().result(), today));
            }
        };
        roomFilter.addValueChangeListener(event -> renderSelectedRoom.run());
        renderSelectedRoom.run();
        content.add(roomFilter, selectedRoomPlan);
        if (today) {
            content.add(currentCommandPreview(roomPlans), woodBurningPreview(roomPlans));
        }
        return content;
    }

    private VerticalLayout plannedActions(List<HeatingPlanSimulationService.SimulationPoint> points,
                                          HeatingPlanSimulationService.SimulationResult result, boolean today) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        Grid<PlanAction> actions = new Grid<>(PlanAction.class, false);
        actions.addColumn(PlanAction::time)
                .setHeader("Time")
                .setWidth("90px")
                .setFlexGrow(0);
        actions.addComponentColumn(action -> wrappingCell(action.action()))
                .setHeader("Plan")
                .setFlexGrow(2);
        actions.addComponentColumn(action -> wrappingCell(action.reason()))
                .setHeader("Why")
                .setFlexGrow(3);
        actions.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        actions.setAllRowsVisible(true);
        actions.setItems(actions(points, result.woodStoveRecommendation(), today));
        section.add(new H3("Planned actions"), actions);
        return section;
    }

    private VerticalLayout currentCommandPreview(List<RoomPlan> roomPlans) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        Grid<CurrentCommandPreview> grid = new Grid<>(CurrentCommandPreview.class, false);
        grid.addColumn(CurrentCommandPreview::room).setHeader("Room").setFlexGrow(1);
        grid.addComponentColumn(row -> wrappingCell(row.thermostat())).setHeader("Thermostat").setFlexGrow(2);
        grid.addColumn(CurrentCommandPreview::command).setHeader("Would command now").setFlexGrow(1);
        grid.addComponentColumn(row -> wrappingCell(row.reason())).setHeader("Why").setFlexGrow(3);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        Instant now = Instant.now();
        List<CurrentCommandPreview> previews = roomPlans.stream()
                .map(plan -> currentCommand(plan, now))
                .toList();
        grid.setItems(previews);
        grid.setAllRowsVisible(true);
        Span warning = new Span("Preview only unless the Active thermostat control panel says ACTIVE. Active commands have priority; expired or disabled planner control leaves the existing Control feature as fallback.");
        warning.getElement().getThemeList().add("badge warning");
        section.add(new H3("Current planned thermostat setpoints"), warning, grid);
        return section;
    }

    private CurrentCommandPreview currentCommand(RoomPlan plan, Instant now) {
        if (plan.result() == null) {
            return new CurrentCommandPreview(plan.room(), deviceLabel(plan.controller()), "No command", plan.planError());
        }
        if (plan.sourceType() != HeatingPlannerHeatSourceType.FLOOR_HEATING) {
            return new CurrentCommandPreview(plan.room(), deviceLabel(plan.controller()), "No command",
                    "The configured heat source is not controllable floor heating.");
        }
        if (plan.controller() == null) {
            return new CurrentCommandPreview(plan.room(), "Not configured", "No command",
                    "No controlling thermostat is selected for this room.");
        }
        HeatingPlanSimulationService.SimulationPoint point = currentPoint(plan.result(), now);
        if (point == null) {
            return new CurrentCommandPreview(plan.room(), deviceLabel(plan.controller()), "No command", "No plan point is available.");
        }
        if (!plan.result().plannerActive()
                || point.mode() == HeatingPlanSimulationService.OperatingMode.INACTIVE) {
            return new CurrentCommandPreview(plan.room(), deviceLabel(plan.controller()), "No command", point.reason());
        }
        return new CurrentCommandPreview(plan.room(), deviceLabel(plan.controller()),
                point.floorSetpoint() + " °C", point.reason());
    }

    private HeatingPlanSimulationService.SimulationPoint currentPoint(HeatingPlanSimulationService.SimulationResult result,
                                                                      Instant now) {
        if (result == null) {
            return null;
        }
        return result.points().stream()
                .filter(candidate -> !candidate.time().isAfter(now))
                .reduce((left, right) -> right)
                .orElseGet(() -> result.points().stream().findFirst().orElse(null));
    }

    private VerticalLayout woodBurningPreview(List<RoomPlan> roomPlans) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        HeatingPlanSimulationService.WoodStoveRecommendation wood = roomPlans.stream()
                .map(RoomPlan::result)
                .map(HeatingPlanSimulationService.SimulationResult::woodStoveRecommendation)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparing(HeatingPlanSimulationService.WoodStoveRecommendation::notifyAt))
                .orElse(null);
        H3 heading = new H3("Simulated wood-burning call");
        if (wood == null) {
            section.add(heading, new Paragraph("No wood-burning call is required by the current plan. The stove must be loaded, the weather gate and expensive-period conditions must match, and the call time must be inside the availability window."));
        } else {
            section.add(heading,
                    new Span("Would call at: " + formatInstant(wood.notifyAt())),
                    new Span("Light: " + wood.loadName() + " (" + wood.woodAmount() + " kg)"),
                    new Span("Useful heat expected: " + formatInstant(wood.releaseStartsAt()) + " – " + formatInstant(wood.releaseEndsAt())),
                    new Span("Reason: " + wood.reason()),
                    new Span("A push notification will be sent at the call time if Heating Planner remains enabled, the stove is still marked loaded, and the time is inside your availability window."));
        }
        return section;
    }

    private Span wrappingCell(String text) {
        Span cell = new Span(text);
        cell.setWidthFull();
        cell.getStyle()
                .set("white-space", "normal")
                .set("overflow-wrap", "anywhere")
                .set("line-height", "1.35");
        return cell;
    }

    private List<PlanAction> actions(List<HeatingPlanSimulationService.SimulationPoint> points,
                                     HeatingPlanSimulationService.WoodStoveRecommendation wood, boolean today) {
        List<PlanAction> actions = new ArrayList<>();
        HeatingPlanSimulationService.OperatingMode previous = null;
        for (var point : points) {
            if (point.mode() != previous) {
                actions.add(new PlanAction(TIME.format(point.time().atZone(ZONE)),
                        switch (point.mode()) {
                            case PREHEAT -> "Preheat floor to " + point.floorSetpoint() + " °C";
                            case DISCHARGE -> "Use stored heat; floor setpoint " + point.floorSetpoint() + " °C";
                            case COMFORT_RECOVERY -> "Protect room comfort";
                            case NORMAL -> "Maintain normal floor temperature";
                            case INACTIVE -> "Heating optimization inactive";
                        }, point.reason()));
                previous = point.mode();
            }
        }
        if (wood != null && wood.notifyAt().atZone(ZONE).toLocalDate()
                .equals(points.getFirst().time().atZone(ZONE).toLocalDate())) {
            actions.add(new PlanAction(TIME.format(wood.notifyAt().atZone(ZONE)),
                    "Push: light " + wood.loadName() + " (" + wood.woodAmount() + " kg)", wood.reason()));
        }
        actions.sort((left, right) -> left.time().compareTo(right.time()));
        return actions;
    }

    private VerticalLayout evidenceContent(List<RoomPlan> roomPlans, SiteEntity site,
                                           List<SiteWeatherEntity> forecast, MarketSeries marketSeries,
                                           PlanEvidenceInputs inputs) {
        VerticalLayout evidence = new VerticalLayout();
        evidence.setPadding(false);
        evidence.setSpacing(true);
        evidence.setWidthFull();
        String siteText = site == null ? "not selected" : site.getName() + " (" + site.getTimezone() + ")";
        String weatherText = site == null ? "no site selected"
                : hasWeatherPlace(site) ? forecastSummary(forecast)
                : "site has no weather place configured; simulation uses the explicit fallback of 0 °C outdoor temperature and 0 m/s wind";
        evidence.add(
                evidenceSection("Overview", evidenceGrid(
                        new EvidenceValue("Generated", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                .withZone(zoneForSite(site)).format(inputs.calculatedAt())),
                        new EvidenceValue("Planner status", plannerStatusText(roomPlans)),
                        new EvidenceValue("Site", siteText),
                        new EvidenceValue("Weather", weatherText)
                )),
                evidenceSection("Prices and gates", evidenceGrid(
                        new EvidenceValue("Market prices", marketSeries.description()),
                        new EvidenceValue("Cheap limit", "≤ " + priceDisplay(inputs.cheapPriceThreshold())
                                + " c/kWh, lower quartile"),
                        new EvidenceValue("Expensive limit", "≥ " + priceDisplay(inputs.expensivePriceThreshold())
                                + " c/kWh, upper quartile"),
                        new EvidenceValue("Price sample", marketSeries.points().size()
                                + " today-and-tomorrow combined-price points, 1 h simulation step"),
                        new EvidenceValue("Planner active below", decimalDisplay(inputs.plannerWeatherThreshold()) + " °C"),
                        new EvidenceValue("Planner gate", plannerGateText(marketSeries.points(),
                                inputs.plannerWeatherThreshold())),
                        new EvidenceValue("Recommend wood below", decimalDisplay(inputs.woodWeatherThreshold()) + " °C")
                )),
                evidenceSection("Latest Sensors", sensorEvidenceGrid(roomPlans)),
                evidenceSection("Room Limits", roomLimitEvidenceGrid(roomPlans)),
                evidenceSection("Wood Stove", evidenceGrid(
                        new EvidenceValue("State", inputs.stoveLoaded() ? "loaded and ready" : "not loaded"),
                        new EvidenceValue("Availability", timeOrDefault(inputs.availableFrom(), LocalTime.of(6, 0))
                                + "–" + timeOrDefault(inputs.availableTo(), LocalTime.of(22, 0))),
                        new EvidenceValue("Load", decimalDisplay(inputs.woodAmount()) + " kg"),
                        new EvidenceValue("Useful heat starts after", durationDisplay(inputs.releaseDelayHours())),
                        new EvidenceValue("Release duration", durationDisplay(inputs.releaseDurationHours())),
                        new EvidenceValue("Initial room-heating rate", "0.35 °C/h")
                )),
                evidenceSection("Thermal Models", modelEvidenceGrid(roomPlans))
        );
        return evidence;
    }

    private VerticalLayout evidenceSection(String title, Component content) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.setWidthFull();
        section.getStyle()
                .set("border-top", "1px solid var(--lumo-contrast-10pct)")
                .set("padding-top", "var(--lumo-space-m)");
        H3 heading = new H3(title);
        heading.getStyle()
                .set("font-size", "var(--lumo-font-size-m)")
                .set("margin", "0 0 var(--lumo-space-s) 0");
        section.add(heading, content);
        return section;
    }

    private FormLayout evidenceGrid(EvidenceValue... values) {
        FormLayout grid = new FormLayout();
        grid.setWidthFull();
        grid.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("650px", 2),
                new FormLayout.ResponsiveStep("1000px", 3));
        for (EvidenceValue value : values) {
            grid.add(evidenceValue(value.label(), value.value()));
        }
        return grid;
    }

    private VerticalLayout evidenceValue(String label, String value) {
        VerticalLayout item = new VerticalLayout();
        item.setPadding(false);
        item.setSpacing(false);
        item.setWidthFull();
        Span labelText = new Span(label);
        labelText.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-weight", "600");
        Span valueText = wrappingCell(value);
        valueText.getStyle().set("font-weight", "500");
        item.add(labelText, valueText);
        return item;
    }

    private Grid<RoomSensorEvidenceRow> sensorEvidenceGrid(List<RoomPlan> roomPlans) {
        Grid<RoomSensorEvidenceRow> grid = new Grid<>(RoomSensorEvidenceRow.class, false);
        grid.setWidthFull();
        grid.addColumn(RoomSensorEvidenceRow::room).setHeader("Room").setFlexGrow(1);
        grid.addComponentColumn(row -> wrappingCell(row.roomTemperature())).setHeader("Room temperature").setFlexGrow(2);
        grid.addComponentColumn(row -> wrappingCell(row.floorTemperature())).setHeader("Floor temperature").setFlexGrow(2);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setAllRowsVisible(true);
        grid.setItems(roomPlans.stream()
                .map(plan -> new RoomSensorEvidenceRow(plan.room(),
                        measurementText(plan.roomMeasurement(), plan.initialRoomTemperature()),
                        measurementText(plan.floorMeasurement(), plan.initialFloorTemperature())))
                .toList());
        return grid;
    }

    private Grid<RoomLimitEvidenceRow> roomLimitEvidenceGrid(List<RoomPlan> roomPlans) {
        Grid<RoomLimitEvidenceRow> grid = new Grid<>(RoomLimitEvidenceRow.class, false);
        grid.setWidthFull();
        grid.addColumn(RoomLimitEvidenceRow::room).setHeader("Room").setFlexGrow(1);
        grid.addComponentColumn(row -> wrappingCell(row.comfort())).setHeader("Comfort").setFlexGrow(2);
        grid.addComponentColumn(row -> wrappingCell(row.floor())).setHeader("Floor").setFlexGrow(3);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setAllRowsVisible(true);
        grid.setItems(roomPlans.stream()
                .map(plan -> new RoomLimitEvidenceRow(plan.room(),
                        "target " + plan.targetRoomTemperature() + " °C, guard "
                                + plan.targetRoomTemperature().subtract(BigDecimal.ONE) + "..."
                                + plan.targetRoomTemperature().add(new BigDecimal("2.50")) + " °C",
                        "normal " + plan.normalFloorTemperature() + " °C, preheat max "
                                + plan.maximumPreheatFloorTemperature() + " °C, absolute max "
                                + plan.absoluteMaximumFloorTemperature() + " °C, discharge "
                                + plan.dischargeFloorSetpoint() + " °C"))
                .toList());
        return grid;
    }

    private Grid<RoomModelEvidenceRow> modelEvidenceGrid(List<RoomPlan> roomPlans) {
        Grid<RoomModelEvidenceRow> grid = new Grid<>(RoomModelEvidenceRow.class, false);
        grid.setWidthFull();
        grid.addColumn(RoomModelEvidenceRow::room).setHeader("Room").setFlexGrow(1);
        grid.addComponentColumn(row -> wrappingCell(row.model())).setHeader("Model").setFlexGrow(4);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setAllRowsVisible(true);
        grid.setItems(roomPlans.stream()
                .map(plan -> new RoomModelEvidenceRow(plan.room(), plan.modelEvidence()))
                .toList());
        return grid;
    }

    private Details roomCalculationBreakdown(List<RoomPlan> roomPlans) {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setWidthFull();
        content.add(new Span("The exact configured comfort and floor limits used for each room are shown below. The simulation comfort guard is target - 1 °C to target + 2.5 °C."));

        Grid<RoomCalculationRow> grid = new Grid<>(RoomCalculationRow.class, false);
        grid.setWidthFull();
        grid.addColumn(RoomCalculationRow::room).setHeader("Room").setFlexGrow(1);
        grid.addComponentColumn(row -> wrappingCell(row.startingState())).setHeader("Starting state").setFlexGrow(2);
        grid.addComponentColumn(row -> wrappingCell(row.limits())).setHeader("Setpoints and limits").setFlexGrow(2);
        grid.addComponentColumn(row -> wrappingCell(row.currentDecision())).setHeader("Current decision").setFlexGrow(2);
        grid.addComponentColumn(row -> wrappingCell(row.reason())).setHeader("Why").setFlexGrow(3);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setAllRowsVisible(true);
        grid.setItems(roomPlans.stream().map(this::roomCalculationRow).toList());
        content.add(grid);

        Details details = new Details("Per-room calculation breakdown", content);
        details.setWidthFull();
        details.setOpened(false);
        return details;
    }

    private RoomCalculationRow roomCalculationRow(RoomPlan plan) {
        String startingState = "room " + measurementText(plan.roomMeasurement(), plan.initialRoomTemperature())
                + "; floor " + measurementText(plan.floorMeasurement(), plan.initialFloorTemperature());
        String limits = "comfort " + plan.targetRoomTemperature() + " °C, comfort guard "
                + plan.targetRoomTemperature().subtract(BigDecimal.ONE) + "..." + plan.targetRoomTemperature().add(new BigDecimal("2.50"))
                + " °C; normal floor " + plan.normalFloorTemperature() + " °C, preheat max "
                + plan.maximumPreheatFloorTemperature() + " °C, absolute max "
                + plan.absoluteMaximumFloorTemperature() + " °C, discharge "
                + plan.dischargeFloorSetpoint() + " °C";
        if (plan.result() == null) {
            return new RoomCalculationRow(plan.room(), startingState, limits, "No plan point", plan.planError());
        }
        HeatingPlanSimulationService.SimulationPoint point = currentPoint(plan.result(), Instant.now());
        if (point == null) {
            return new RoomCalculationRow(plan.room(), startingState, limits, "No plan point", "No plan point is available.");
        }
        String decision = point.mode() + ": floor setpoint " + point.floorSetpoint() + " °C, heating "
                + (point.heating() ? "on" : "off");
        return new RoomCalculationRow(plan.room(), startingState, limits, decision, point.reason());
    }

    private String plannerStatusText(List<RoomPlan> roomPlans) {
        List<HeatingPlanSimulationService.SimulationResult> results = roomPlans.stream()
                .map(RoomPlan::result)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (results.isEmpty()) {
            return "unavailable — " + roomPlans.stream()
                    .map(RoomPlan::planError)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse("no valid room plan");
        }
        long activeCount = results.stream().filter(HeatingPlanSimulationService.SimulationResult::plannerActive).count();
        String reason = results.stream()
                .map(HeatingPlanSimulationService.SimulationResult::plannerStatusReason)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse("no planner status reason");
        if (activeCount == results.size()) {
            return "active — " + reason;
        }
        if (activeCount == 0) {
            return "inactive — " + reason + "; existing heating controls keep priority";
        }
        return "partially active — " + activeCount + " of " + results.size()
                + " room plans pass the planner activation gate";
    }

    private String plannerGateText(List<HeatingPlanSimulationService.MarketPoint> market,
                                   Double plannerWeatherThreshold) {
        if (market == null || market.isEmpty()) {
            return "inactive — no forecast points are available for the today-and-tomorrow horizon";
        }
        BigDecimal threshold = BigDecimal.valueOf(plannerWeatherThreshold == null ? 5.0 : plannerWeatherThreshold);
        BigDecimal coldest = market.stream()
                .map(HeatingPlanSimulationService.MarketPoint::outdoorTemperature)
                .filter(java.util.Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
        if (coldest == null) {
            return "inactive — no outdoor-temperature forecast value is available";
        }
        if (coldest.compareTo(threshold) < 0) {
            return "active — coldest forecast " + decimalDisplay(coldest.doubleValue())
                    + " °C is below " + decimalDisplay(threshold.doubleValue()) + " °C";
        }
        return "inactive — coldest forecast " + decimalDisplay(coldest.doubleValue())
                + " °C is not below " + decimalDisplay(threshold.doubleValue())
                + " °C, so existing heating controls keep priority";
    }

    private HeatingPlanSimulationService.SimulationRequest simulationRequest(boolean stoveLoaded, LocalTime availableFrom,
                                                                             LocalTime availableTo, Double woodAmount,
                                                                             Double releaseDelayHours,
                                                                             Double releaseDurationHours,
                                                                             Double plannerWeatherThreshold,
                                                                             Double woodWeatherThreshold,
                                                                             BigDecimal initialFloorTemperature,
                                                                             BigDecimal initialRoomTemperature,
                                                                             BigDecimal targetRoomTemperature,
                                                                             BigDecimal normalFloorTemperature,
                                                                             BigDecimal maximumPreheatFloorTemperature,
                                                                             BigDecimal absoluteMaximumFloorTemperature,
                                                                             BigDecimal dischargeFloorSetpoint,
                                                                             List<HeatingPlanSimulationService.MarketPoint> market,
                                                                             HeatingPlanSimulationService.ThermalModel model,
                                                                             boolean roomMeasurementFresh,
                                                                             boolean floorMeasurementFresh,
                                                                             HeatingPlanSimulationService.PriceThresholds priceThresholds) {
        ZonedDateTime start = LocalDate.now(ZONE).atStartOfDay(ZONE);
        BigDecimal initialFloor = initialFloorTemperature == null ? new BigDecimal("22.00") : initialFloorTemperature;
        BigDecimal initialRoom = initialRoomTemperature == null ? new BigDecimal("21.00") : initialRoomTemperature;
        BigDecimal target = targetRoomTemperature == null ? new BigDecimal("21.00") : targetRoomTemperature;
        BigDecimal normalFloor = normalFloorTemperature == null ? new BigDecimal("23.00") : normalFloorTemperature;
        BigDecimal maximumPreheatFloor = maximumPreheatFloorTemperature == null
                ? new BigDecimal("27.00") : maximumPreheatFloorTemperature;
        BigDecimal absoluteMaximumFloor = absoluteMaximumFloorTemperature == null
                ? new BigDecimal("29.00") : absoluteMaximumFloorTemperature;
        BigDecimal dischargeFloor = dischargeFloorSetpoint == null ? new BigDecimal("19.00") : dischargeFloorSetpoint;
        var settings = new HeatingPlanSimulationService.Settings(Duration.ofHours(1),
                priceThresholds.cheapPriceThreshold(), priceThresholds.expensivePriceThreshold(),
                normalFloor, maximumPreheatFloor,
                absoluteMaximumFloor, dischargeFloor, target.subtract(BigDecimal.ONE),
                target.add(new BigDecimal("2.50")),
                BigDecimal.valueOf(plannerWeatherThreshold));
        List<HeatingPlanSimulationService.StoveAvailability> availability = List.of(
                new HeatingPlanSimulationService.StoveAvailability(
                        start.with(timeOrDefault(availableFrom, LocalTime.of(6, 0))).toInstant(),
                        start.with(timeOrDefault(availableTo, LocalTime.of(22, 0))).toInstant()),
                new HeatingPlanSimulationService.StoveAvailability(
                        start.plusDays(1).with(timeOrDefault(availableFrom, LocalTime.of(6, 0))).toInstant(),
                        start.plusDays(1).with(timeOrDefault(availableTo, LocalTime.of(22, 0))).toInstant())
        );
        var stove = new HeatingPlanSimulationService.WoodStoveSettings(true, stoveLoaded, "Static wood load",
                BigDecimal.valueOf(woodAmount), Duration.ofMinutes(Math.round(releaseDelayHours * 60)),
                Duration.ofMinutes(Math.round(releaseDurationHours * 60)), new BigDecimal("0.35"),
                BigDecimal.valueOf(woodWeatherThreshold), availability);
        return new HeatingPlanSimulationService.SimulationRequest(initialFloor, initialRoom,
                settings, model, market, stove, floorMeasurementFresh, roomMeasurementFresh);
    }

    private HeatingPlanSimulationService.ThermalModel defaultThermalModel() {
        return new HeatingPlanSimulationService.ThermalModel(new BigDecimal("2"), new BigDecimal("0.8"),
                new BigDecimal("0.06"), new BigDecimal("0.012"), new BigDecimal("0.001"));
    }

    private MeasurementInputs measurementInputs(RoomOverview room, HeatingPlannerMeasurementService measurementService,
                                                Instant now) {
        HeatingPlannerRoomEntity measurementRoom = new HeatingPlannerRoomEntity();
        measurementRoom.setRoomSensorDevice(room.roomSensor());
        measurementRoom.setRoomSensorMeasurementKey(room.roomSensor() == null
                ? null : HeatingPlannerMeasurementService.DEFAULT_TEMPERATURE_KEY);
        measurementRoom.setFloorSensorDevice(room.floorSensor());
        measurementRoom.setFloorSensorMeasurementKey(room.floorSensor() == null
                ? null : HeatingPlannerMeasurementService.DEFAULT_TEMPERATURE_KEY);
        measurementRoom.getHeatSources().add(HeatingPlannerRoomHeatSourceEntity.builder()
                .enabled(true)
                .controllingDevice(room.controller())
                .build());
        HeatingPlannerMeasurementService.LatestMeasurement roomMeasurement =
                measurementService.latestFreshRoomTemperature(measurementRoom, now);
        HeatingPlannerMeasurementService.LatestMeasurement floorMeasurement;
        floorMeasurement = measurementService.latestFreshFloorTemperature(measurementRoom, now);
        BigDecimal roomTemperature = roomMeasurement.fresh() ? roomMeasurement.value() : new BigDecimal("21.00");
        BigDecimal floorTemperature = floorMeasurement.fresh() ? floorMeasurement.value() : new BigDecimal("22.00");
        return new MeasurementInputs(roomTemperature, floorTemperature, roomMeasurement, floorMeasurement);
    }

    private String sensorEvidence(List<RoomPlan> roomPlans) {
        return roomPlans.stream()
                .map(plan -> plan.room() + " room " + measurementText(plan.roomMeasurement(), plan.initialRoomTemperature())
                        + ", floor " + measurementText(plan.floorMeasurement(), plan.initialFloorTemperature()))
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String roomLimitEvidence(RoomPlan plan) {
        return plan.room() + " target " + plan.targetRoomTemperature() + " °C, comfort guard "
                + plan.targetRoomTemperature().subtract(BigDecimal.ONE) + "…"
                + plan.targetRoomTemperature().add(new BigDecimal("2.50")) + " °C, floor normal "
                + plan.normalFloorTemperature() + " °C, preheat max "
                + plan.maximumPreheatFloorTemperature() + " °C, absolute max "
                + plan.absoluteMaximumFloorTemperature() + " °C, discharge "
                + plan.dischargeFloorSetpoint() + " °C";
    }

    private String thermalModelEvidence(String source, HeatingPlanSimulationService.ThermalModel model) {
        return source + " [floor heating " + model.floorHeatingRate()
                + " °C/h, floor-to-room " + model.floorToRoomRate()
                + " °C/h, outdoor loss " + model.roomOutdoorLossRate()
                + ", wind loss " + model.windLossRate() + "]";
    }

    private String measurementText(HeatingPlannerMeasurementService.LatestMeasurement measurement, BigDecimal fallback) {
        if (measurement.fresh()) {
            return measurement.value() + " °C fresh at " + formatInstant(measurement.measuredAt());
        }
        if (measurement.freshness() == HeatingPlannerMeasurementService.Freshness.STALE) {
            return "stale at " + formatInstant(measurement.measuredAt()) + ", using " + fallback + " °C estimate";
        }
        return "missing, using " + fallback + " °C estimate";
    }

    private MarketSeries marketSeries(com.nitramite.porssiohjain.entity.AccountEntity account, SiteEntity site,
                                      BigDecimal taxPercent, ElectricityContractEntity transferContract,
                                      List<SiteWeatherEntity> forecast) {
        ZonedDateTime start = LocalDate.now(ZONE).atStartOfDay(ZONE);
        ZonedDateTime end = start.plusDays(2);
        if (account == null) {
            return fallbackMarketSeries(start, forecast, "fallback prices because account is unavailable");
        }
        String marketIndex = NordpoolMarket.normalize(account.getMarketIndexName());
        List<NordpoolEntity> prices = nordpoolRepository.findPricesBetween(marketIndex, start.toInstant(), end.toInstant());
        if (prices.isEmpty()) {
            return fallbackMarketSeries(start, forecast, "fallback prices because Nordpool rows are missing for " + marketIndex);
        }
        ZoneId zone = zoneForSite(site);
        BigDecimal taxMultiplier = BigDecimal.ONE.add(taxPercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
        List<HeatingPlanSimulationService.MarketPoint> points = prices.stream()
                .sorted(Comparator.comparing(NordpoolEntity::getDeliveryStart))
                .map(price -> {
                    BigDecimal nordpoolWithTax = price.getPriceFi()
                            .multiply(BigDecimal.valueOf(0.1))
                            .multiply(taxMultiplier)
                            .setScale(4, RoundingMode.HALF_UP);
                    BigDecimal combinedPrice = nordpoolWithTax.add(resolveTransferPrice(transferContract,
                            price.getDeliveryStart(), zone));
                    WeatherValues weather = weatherAt(forecast, price.getDeliveryStart(), price.getDeliveryStart().atZone(zone).getHour());
                    return new HeatingPlanSimulationService.MarketPoint(price.getDeliveryStart(), combinedPrice,
                            weather.temperature(), weather.windSpeedMs());
                })
                .toList();
        String transferText = transferContract == null ? "no transfer contract" : "transfer " + transferContract.getName();
        return new MarketSeries(points, "Nordpool " + marketIndex + " with " + taxPercent + "% VAT and " + transferText
                + " (" + points.size() + " rows)");
    }

    private MarketSeries fallbackMarketSeries(ZonedDateTime start, List<SiteWeatherEntity> forecast, String reason) {
        List<HeatingPlanSimulationService.MarketPoint> market = new ArrayList<>();
        for (int hour = 0; hour < 48; hour++) {
            int localHour = start.plusHours(hour).getHour();
            BigDecimal price = localHour >= 7 && localHour < 11 ? new BigDecimal("26.0")
                    : localHour >= 17 && localHour < 21 ? new BigDecimal("31.0")
                    : localHour >= 1 && localHour < 6 ? new BigDecimal("3.5") : new BigDecimal("11.0");
            Instant time = start.plusHours(hour).toInstant();
            WeatherValues weather = weatherAt(forecast, time, localHour);
            market.add(new HeatingPlanSimulationService.MarketPoint(time, price, weather.temperature(), weather.windSpeedMs()));
        }
        return new MarketSeries(market, reason);
    }

    private WeatherValues weatherAt(List<SiteWeatherEntity> forecast, Instant time, int localHour) {
        Optional<SiteWeatherEntity> weather = nearestForecast(forecast, time);
        BigDecimal outdoor = weather.map(SiteWeatherEntity::getTemperature)
                .orElse(BigDecimal.ZERO);
        BigDecimal wind = weather.map(SiteWeatherEntity::getWindSpeedMs)
                .orElse(BigDecimal.ZERO);
        return new WeatherValues(outdoor, wind);
    }

    private BigDecimal resolveTransferPrice(ElectricityContractEntity transferContract, Instant deliveryStart, ZoneId zone) {
        if (transferContract == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal staticPrice = transferContract.getStaticPrice();
        BigDecimal nightPrice = transferContract.getNightPrice();
        BigDecimal dayPrice = transferContract.getDayPrice();
        BigDecimal taxAmount = transferContract.getTaxAmount() != null ? transferContract.getTaxAmount() : BigDecimal.ZERO;
        if (staticPrice != null && dayPrice == null && nightPrice == null) {
            return staticPrice.add(taxAmount);
        }
        if (dayPrice != null || nightPrice != null) {
            int hour = deliveryStart.atZone(zone).getHour();
            boolean isNight = hour >= 22 || hour < 7;
            BigDecimal basePrice = isNight ? nightPrice : dayPrice;
            return basePrice != null ? basePrice.add(taxAmount) : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    private ZoneId zoneForSite(SiteEntity site) {
        if (site == null || site.getTimezone() == null || site.getTimezone().isBlank()) {
            return ZONE;
        }
        return ZoneId.of(site.getTimezone());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.findAuthenticatedAccount(authService) == null) {
            event.forwardTo(LoginView.class);
        }
    }

    private record PlanAction(String time, String action, String reason) {
    }

    private record RoomPlan(String room, HeatingPlannerHeatSourceType sourceType, DeviceEntity controller,
                            BigDecimal initialRoomTemperature,
                            BigDecimal initialFloorTemperature,
                            HeatingPlannerMeasurementService.LatestMeasurement roomMeasurement,
                            HeatingPlannerMeasurementService.LatestMeasurement floorMeasurement,
                            BigDecimal targetRoomTemperature,
                            BigDecimal normalFloorTemperature,
                            BigDecimal maximumPreheatFloorTemperature,
                            BigDecimal absoluteMaximumFloorTemperature,
                            BigDecimal dischargeFloorSetpoint,
                            String modelEvidence,
                            HeatingPlanSimulationService.SimulationResult result,
                            String planError) {
    }

    private record RoomDayPlan(RoomPlan roomPlan, List<HeatingPlanSimulationService.SimulationPoint> points) {
    }

    private record CurrentCommandPreview(String room, String thermostat, String command, String reason) {
    }

    private record RoomCalculationRow(String room, String startingState, String limits,
                                      String currentDecision, String reason) {
    }

    private record EvidenceValue(String label, String value) {
    }

    private record RoomSensorEvidenceRow(String room, String roomTemperature, String floorTemperature) {
    }

    private record RoomLimitEvidenceRow(String room, String comfort, String floor) {
    }

    private record RoomModelEvidenceRow(String room, String model) {
    }

    private record MarketSeries(List<HeatingPlanSimulationService.MarketPoint> points, String description) {
    }

    private record WeatherValues(BigDecimal temperature, BigDecimal windSpeedMs) {
    }

    private record RecentMeasurementRow(String measuredAt, String receivedAt, String device, String zigbeeIeee,
                                        String profile, String type, String value,
                                        ZigbeeMeasurementType measurementType, String measurementKey) {
    }

    private record MeasurementInputs(
            BigDecimal roomTemperature,
            BigDecimal floorTemperature,
            HeatingPlannerMeasurementService.LatestMeasurement roomMeasurement,
            HeatingPlannerMeasurementService.LatestMeasurement floorMeasurement
    ) {
    }

    private record PlanEvidenceInputs(
            Instant calculatedAt,
            Double plannerWeatherThreshold,
            Double woodWeatherThreshold,
            boolean stoveLoaded,
            LocalTime availableFrom,
            LocalTime availableTo,
            Double woodAmount,
            Double releaseDelayHours,
            Double releaseDurationHours,
            BigDecimal cheapPriceThreshold,
            BigDecimal expensivePriceThreshold
    ) {
    }

    private BigDecimal decimalOrDefault(Double value, String fallback) {
        return value == null ? new BigDecimal(fallback) : BigDecimal.valueOf(value);
    }

    private Integer minutesFromHours(Double value, int fallbackMinutes) {
        return value == null ? fallbackMinutes : Math.toIntExact(Math.round(value * 60));
    }

    private String decimalDisplay(Double value) {
        return BigDecimal.valueOf(value == null ? 8.0 : value).stripTrailingZeros().toPlainString();
    }

    private String priceDisplay(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String durationDisplay(Double hours) {
        int minutes = minutesFromHours(hours, 0);
        if (minutes < 60) {
            return minutes + " min";
        }
        int wholeHours = minutes / 60;
        int remainingMinutes = minutes % 60;
        return remainingMinutes == 0
                ? wholeHours + " h"
                : wholeHours + " h " + remainingMinutes + " min";
    }

    private LocalTime timeOrDefault(LocalTime value, LocalTime fallback) {
        return value == null ? fallback : value;
    }

    private Optional<SiteEntity> preferredSite(List<SiteEntity> sites, Optional<Long> preferredSiteId) {
        if (preferredSiteId.isPresent()) {
            Optional<SiteEntity> preferred = sites.stream()
                    .filter(site -> site.getId().equals(preferredSiteId.get()))
                    .findFirst();
            if (preferred.isPresent()) {
                return preferred;
            }
        }
        return sites.stream().filter(SiteEntity::getEnabled).findFirst();
    }

    private void savePlannerSettingsSilently(HeatingPlannerConfigurationService configurationService, Long accountId,
                                             SiteEntity site, Checkbox plannerEnabled,
                                             NumberField plannerWeatherThreshold, NumberField woodWeatherThreshold,
                                             NumberField taxPercent,
                                             ComboBox<ElectricityContractEntity> transferContract,
                                             Checkbox loaded, TimePicker availableFrom, TimePicker availableTo,
                                             NumberField woodAmount, NumberField releaseDelay,
                                             NumberField releaseDuration) {
        if (accountId == null || site == null) {
            return;
        }
        try {
            savePlannerSettings(configurationService, accountId, site, plannerEnabled, plannerWeatherThreshold,
                    woodWeatherThreshold, taxPercent, transferContract, loaded, availableFrom, availableTo,
                    woodAmount, releaseDelay, releaseDuration);
        } catch (IllegalArgumentException ex) {
            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void savePlannerSettings(HeatingPlannerConfigurationService configurationService, Long accountId,
                                     SiteEntity site, Checkbox plannerEnabled,
                                     NumberField plannerWeatherThreshold, NumberField woodWeatherThreshold,
                                     NumberField taxPercent,
                                     ComboBox<ElectricityContractEntity> transferContract,
                                     Checkbox loaded, TimePicker availableFrom, TimePicker availableTo,
                                     NumberField woodAmount, NumberField releaseDelay,
                                     NumberField releaseDuration) {
        configurationService.saveSettings(accountId, site.getId(),
                new HeatingPlannerConfigurationService.SettingsConfiguration(
                        plannerEnabled.getValue(),
                        decimalOrDefault(plannerWeatherThreshold.getValue(), "5.00"),
                        decimalOrDefault(woodWeatherThreshold.getValue(), "0.00"),
                        decimalOrDefault(taxPercent.getValue(), "25.50"),
                        transferContract.getValue() == null ? null : transferContract.getValue().getId(),
                        loaded.getValue(),
                        timeOrDefault(availableFrom.getValue(), LocalTime.of(6, 0)),
                        timeOrDefault(availableTo.getValue(), LocalTime.of(22, 0)),
                        decimalOrDefault(woodAmount.getValue(), "8.00"),
                        minutesFromHours(releaseDelay.getValue(), 45),
                        minutesFromHours(releaseDuration.getValue(), 360)
                ));
    }

    private void loadConfiguration(HeatingPlannerConfigurationService configurationService, Long accountId, SiteEntity site,
                                   AtomicBoolean loadingConfiguration, Checkbox plannerEnabled, NumberField plannerWeatherThreshold,
                                   NumberField woodWeatherThreshold, NumberField taxPercent,
                                   ComboBox<ElectricityContractEntity> transferContract,
                                   Checkbox loaded, TimePicker availableFrom, TimePicker availableTo,
                                   NumberField woodAmount, NumberField releaseDelay, NumberField releaseDuration,
                                   List<RoomOverview> roomRows,
                                   Grid<RoomOverview> rooms, List<DeviceEntity> thermostats,
                                   List<DeviceEntity> temperatureSensors,
                                   List<DeviceEntity> floorSensors,
                                   List<ElectricityContractEntity> transferContracts) {
        roomRows.clear();
        loadingConfiguration.set(true);
        if (accountId != null && site != null) {
            HeatingPlannerConfigurationService.Configuration configuration = configurationService.configuration(accountId, site.getId());
            plannerEnabled.setValue(configuration.enabled());
            plannerWeatherThreshold.setValue(configuration.plannerActiveBelowTemperature().doubleValue());
            woodWeatherThreshold.setValue(configuration.woodRecommendationBelowTemperature().doubleValue());
            taxPercent.setValue(configuration.taxPercent().doubleValue());
            transferContract.clear();
            transferContracts.stream()
                    .filter(contract -> configuration.transferContractId() != null
                            && contract.getId().equals(configuration.transferContractId()))
                    .findFirst()
                    .ifPresent(transferContract::setValue);
            loaded.setValue(configuration.stoveLoaded());
            availableFrom.setValue(timeOrDefault(configuration.stoveAvailableFrom(), LocalTime.of(6, 0)));
            availableTo.setValue(timeOrDefault(configuration.stoveAvailableTo(), LocalTime.of(22, 0)));
            woodAmount.setValue(configuration.woodAmount().doubleValue());
            releaseDelay.setValue(configuration.woodReleaseDelayMinutes() / 60.0);
            releaseDuration.setValue(configuration.woodReleaseDurationMinutes() / 60.0);
            configuration.rooms().stream()
                    .map(room -> new RoomOverview(
                            room.name(),
                            room.sourceType(),
                            room.targetRoomTemperature(),
                            room.normalFloorTemperature(),
                            room.maximumPreheatFloorTemperature(),
                            room.absoluteMaximumFloorTemperature(),
                            room.dischargeFloorSetpoint(),
                            thermostats.stream()
                                    .filter(device -> room.controllingDeviceId() != null
                                            && device.getId().equals(room.controllingDeviceId()))
                                    .findFirst()
                                    .orElse(null),
                            temperatureSensors.stream()
                                    .filter(device -> room.roomSensorDeviceId() != null
                                            && device.getId().equals(room.roomSensorDeviceId()))
                                    .findFirst()
                                    .orElse(null),
                            floorSensors.stream()
                                    .filter(device -> room.floorSensorDeviceId() != null
                                            && device.getId().equals(room.floorSensorDeviceId()))
                                    .findFirst().orElse(null)
                    ))
                    .forEach(roomRows::add);
        } else {
            plannerEnabled.setValue(false);
            taxPercent.setValue(25.5);
            transferContract.clear();
            loaded.setValue(false);
            availableFrom.setValue(LocalTime.of(6, 0));
            availableTo.setValue(LocalTime.of(22, 0));
            woodAmount.setValue(8.0);
            releaseDelay.setValue(0.75);
            releaseDuration.setValue(6.0);
        }
        loadingConfiguration.set(false);
        rooms.getDataProvider().refreshAll();
    }

    private void updateSiteWeatherStatus(Span status, Button configureButton, SiteEntity site) {
        if (site == null) {
            status.setText("Select a site before using real weather forecast data.");
            configureButton.setVisible(false);
            return;
        }
        if (!hasWeatherPlace(site)) {
            status.setText("Selected site has no weather place. Open Sites and set the weather place.");
            configureButton.setVisible(true);
            return;
        }
        List<SiteWeatherEntity> forecast = forecastForHorizon(site);
        status.setText(forecastSummary(forecast));
        configureButton.setVisible(false);
    }

    private List<SiteWeatherEntity> forecastForHorizon(SiteEntity site) {
        if (site == null || !hasWeatherPlace(site)) {
            return List.of();
        }
        ZonedDateTime start = LocalDate.now(ZONE).atStartOfDay(ZONE);
        return siteWeatherRepository.findBySiteAndForecastTimeBetweenOrderByForecastTimeAsc(
                site, start.toInstant(), start.plusDays(2).toInstant());
    }

    private void updateWeatherForecastChart(VerticalLayout host, SiteEntity site) {
        host.removeAll();
        if (site == null) {
            host.add(new Paragraph("Select a site to inspect its weather forecast."));
            return;
        }
        List<SiteWeatherEntity> forecast = forecastForHorizon(site);
        if (forecast.isEmpty()) {
            host.add(new Paragraph("No stored weather forecast rows for today and tomorrow."));
            return;
        }
        host.add(new H3("Weather forecast"), new SiteWeatherForecastChart(forecast, zoneForSite(site)));
    }

    private Optional<SiteWeatherEntity> nearestForecast(List<SiteWeatherEntity> forecast, Instant time) {
        return forecast.stream()
                .min((left, right) -> Long.compare(
                        Math.abs(Duration.between(left.getForecastTime(), time).toMinutes()),
                        Math.abs(Duration.between(right.getForecastTime(), time).toMinutes())));
    }

    private String forecastSummary(List<SiteWeatherEntity> forecast) {
        if (forecast.isEmpty()) {
            return "No stored forecast rows for today and tomorrow; simulation uses the explicit fallback of 0 °C outdoor temperature and 0 m/s wind.";
        }
        BigDecimal min = forecast.stream().map(SiteWeatherEntity::getTemperature).filter(value -> value != null)
                .min(BigDecimal::compareTo).orElse(null);
        BigDecimal max = forecast.stream().map(SiteWeatherEntity::getTemperature).filter(value -> value != null)
                .max(BigDecimal::compareTo).orElse(null);
        Instant fetchedAt = forecast.stream().map(SiteWeatherEntity::getFetchedAt).filter(value -> value != null)
                .max(Instant::compareTo).orElse(null);
        String range = min == null || max == null ? "temperature unavailable" : min + "…" + max + " °C";
        String fetched = fetchedAt == null ? "fetch time unavailable"
                : "fetched " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONE).format(fetchedAt);
        return "Using " + forecast.size() + " stored forecast rows for today and tomorrow, " + range + ", " + fetched + ".";
    }

    private boolean hasWeatherPlace(SiteEntity site) {
        return site.getWeatherPlace() != null && !site.getWeatherPlace().isBlank();
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE).format(instant);
    }

    private String deviceLabel(DeviceEntity device) {
        if (device == null) {
            return "";
        }
        return device.getDeviceName() + " · " + device.getDevicePlatform();
    }

    private static final class RoomOverview {
        private static final BigDecimal NORMAL_FLOOR_OFFSET = new BigDecimal("2.00");
        private static final BigDecimal ABSOLUTE_MAXIMUM_OFFSET = new BigDecimal("2.00");
        private static final BigDecimal DISCHARGE_OFFSET = new BigDecimal("2.00");
        private String room;
        private HeatingPlannerHeatSourceType heatSource;
        private BigDecimal targetRoomTemperature;
        private BigDecimal normalFloorTemperature;
        private BigDecimal maximumPreheatFloorTemperature;
        private BigDecimal absoluteMaximumFloorTemperature;
        private BigDecimal dischargeFloorSetpoint;
        private DeviceEntity controller;
        private DeviceEntity roomSensor;
        private DeviceEntity floorSensor;

        private RoomOverview(String room, HeatingPlannerHeatSourceType heatSource, BigDecimal targetRoomTemperature,
                             BigDecimal normalFloorTemperature, BigDecimal maximumPreheatFloorTemperature,
                             BigDecimal absoluteMaximumFloorTemperature, BigDecimal dischargeFloorSetpoint,
                             DeviceEntity controller, DeviceEntity roomSensor, DeviceEntity floorSensor) {
            this.room = room;
            this.heatSource = heatSource;
            this.targetRoomTemperature = targetRoomTemperature == null ? new BigDecimal("21.00") : targetRoomTemperature;
            this.maximumPreheatFloorTemperature = maximumPreheatFloorTemperature == null
                    ? new BigDecimal("27.00") : maximumPreheatFloorTemperature;
            deriveHiddenFloorSetpoints();
            this.controller = controller;
            this.roomSensor = roomSensor;
            this.floorSensor = floorSensor;
        }

        private String room() {
            return room;
        }

        private void setRoom(String room) {
            this.room = room;
        }

        private HeatingPlannerHeatSourceType heatSource() {
            return heatSource;
        }

        private void setHeatSource(HeatingPlannerHeatSourceType heatSource) {
            this.heatSource = heatSource;
        }

        private BigDecimal targetRoomTemperature() {
            return targetRoomTemperature;
        }

        private void setTargetRoomTemperature(BigDecimal targetRoomTemperature) {
            this.targetRoomTemperature = targetRoomTemperature;
            deriveHiddenFloorSetpoints();
        }

        private BigDecimal normalFloorTemperature() {
            return normalFloorTemperature;
        }

        private void setNormalFloorTemperature(BigDecimal normalFloorTemperature) {
            this.normalFloorTemperature = normalFloorTemperature;
        }

        private BigDecimal maximumPreheatFloorTemperature() {
            return maximumPreheatFloorTemperature;
        }

        private void setMaximumPreheatFloorTemperature(BigDecimal maximumPreheatFloorTemperature) {
            this.maximumPreheatFloorTemperature = maximumPreheatFloorTemperature;
            deriveHiddenFloorSetpoints();
        }

        private BigDecimal absoluteMaximumFloorTemperature() {
            return absoluteMaximumFloorTemperature;
        }

        private void setAbsoluteMaximumFloorTemperature(BigDecimal absoluteMaximumFloorTemperature) {
            this.absoluteMaximumFloorTemperature = absoluteMaximumFloorTemperature;
        }

        private BigDecimal dischargeFloorSetpoint() {
            return dischargeFloorSetpoint;
        }

        private void setDischargeFloorSetpoint(BigDecimal dischargeFloorSetpoint) {
            this.dischargeFloorSetpoint = dischargeFloorSetpoint;
        }

        private void deriveHiddenFloorSetpoints() {
            if (targetRoomTemperature == null) {
                targetRoomTemperature = new BigDecimal("21.00");
            }
            if (maximumPreheatFloorTemperature == null) {
                maximumPreheatFloorTemperature = new BigDecimal("27.00");
            }
            normalFloorTemperature = targetRoomTemperature.add(NORMAL_FLOOR_OFFSET);
            if (maximumPreheatFloorTemperature.compareTo(normalFloorTemperature) < 0) {
                maximumPreheatFloorTemperature = normalFloorTemperature;
            }
            absoluteMaximumFloorTemperature = maximumPreheatFloorTemperature.add(ABSOLUTE_MAXIMUM_OFFSET);
            dischargeFloorSetpoint = targetRoomTemperature.subtract(DISCHARGE_OFFSET);
        }

        private DeviceEntity controller() {
            return controller;
        }

        private void setController(DeviceEntity controller) {
            this.controller = controller;
        }

        private DeviceEntity roomSensor() {
            return roomSensor;
        }

        private void setRoomSensor(DeviceEntity roomSensor) {
            this.roomSensor = roomSensor;
        }

        private DeviceEntity floorSensor() {
            return floorSensor;
        }

        private void setFloorSensor(DeviceEntity floorSensor) {
            this.floorSensor = floorSensor;
        }
    }
}
