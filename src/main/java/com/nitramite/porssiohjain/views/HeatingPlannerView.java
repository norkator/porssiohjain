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
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.enums.ContractType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerConfigurationService;
import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService;
import com.nitramite.porssiohjain.services.nordpool.NordpoolMarket;
import com.nitramite.porssiohjain.views.components.HeatingPlanChart;
import com.nitramite.porssiohjain.views.components.SiteWeatherForecastChart;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
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
    private final SiteWeatherRepository siteWeatherRepository;
    private final NordpoolRepository nordpoolRepository;

    public HeatingPlannerView(AuthService authService, HeatingPlanSimulationService simulationService,
                              SiteRepository siteRepository, SiteWeatherRepository siteWeatherRepository,
                              DeviceRepository deviceRepository,
                              NordpoolRepository nordpoolRepository,
                              ElectricityContractRepository contractRepository,
                              HeatingPlannerConfigurationService configurationService) {
        this.authService = authService;
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
        List<ElectricityContractEntity> transferContracts = account == null ? List.of()
                : contractRepository.findByAccountId(account.getId()).stream()
                .filter(contract -> contract.getType() == ContractType.TRANSFER)
                .toList();
        List<RoomOverview> roomRows = new ArrayList<>();
        AtomicBoolean loadingConfiguration = new AtomicBoolean(false);

        VerticalLayout card = new VerticalLayout();
        card.addClassName("responsive-card");
        card.setWidthFull();
        card.setMaxWidth("1200px");
        card.setAlignItems(Alignment.STRETCH);

        Button back = new Button("Back", VaadinIcon.ARROW_LEFT.create(), e -> getUI()
                .ifPresent(ui -> ui.navigate(HomeView.class)));
        H1 title = new H1("Heating Planner");
        title.getStyle().set("margin", "0");
        Checkbox plannerEnabled = new Checkbox("Enabled", false);
        plannerEnabled.setHelperText("Disabling keeps the room configuration but prevents planner use.");
        Span mockBadge = new Span("MOCK DATA · SIMULATION ONLY");
        mockBadge.getElement().getThemeList().add("badge warning");
        HorizontalLayout heading = new HorizontalLayout(title, plannerEnabled, mockBadge);
        heading.setAlignItems(Alignment.CENTER);

        Paragraph summary = new Paragraph("Whole-house plan · charge floor heating when electricity is cheap and recommend wood before expensive periods");

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
        NumberField plannerWeatherThreshold = numberField("Planner active below (°C)", 5, -40, 20);
        NumberField woodWeatherThreshold = numberField("Recommend wood below (°C)", 0, -40, 20);
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

        Grid<RoomOverview> rooms = roomOverviewGrid(roomRows, thermostats, temperatureSensors);
        Button addRoom = new Button("Add room", VaadinIcon.PLUS.create(), event -> {
            roomRows.add(new RoomOverview("New room", HeatingPlannerHeatSourceType.FLOOR_HEATING, new BigDecimal("21.00"),
                    null, null));
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

        VerticalLayout planHost = new VerticalLayout();
        planHost.setPadding(false);
        planHost.setWidthFull();
        Runnable calculate = () -> {
            planHost.removeAll();
            SiteEntity selectedSite = siteSelect.getValue();
            List<SiteWeatherEntity> forecast = forecastForHorizon(selectedSite);
            MarketSeries marketSeries = marketSeries(account, selectedSite, decimalOrDefault(taxPercent.getValue(), "25.50"),
                    transferContract.getValue(), forecast);
            var request = simulationRequest(loaded.getValue(), availableFrom.getValue(), availableTo.getValue(),
                    woodAmount.getValue(), releaseDelay.getValue(), releaseDuration.getValue(),
                    plannerWeatherThreshold.getValue(), woodWeatherThreshold.getValue(), representativeTarget(roomRows),
                    marketSeries.points());
            planHost.add(planContent(simulationService.simulate(request), selectedSite, forecast, marketSeries));
        };
        Button recalculate = new Button("Recalculate mock plan", VaadinIcon.REFRESH.create(), event -> calculate.run());
        recalculate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
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
                                        row.controller() == null ? null : row.controller().getId(),
                                        row.roomSensor() == null ? null : row.roomSensor().getId()
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
                Notification.show(event.getValue() ? "Heating Planner enabled" : "Heating Planner disabled")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
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
                    releaseDelay, releaseDuration, roomRows, rooms, thermostats, temperatureSensors, transferContracts);
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
                releaseDelay, releaseDuration, roomRows, rooms, thermostats, temperatureSensors, transferContracts);
        calculate.run();

        card.add(back, heading, summary, siteConfiguration, roomConfiguration, stoveConfiguration,
                stoveHeatProfileConfiguration, recalculate, planHost);
        add(card);
    }

    private VerticalLayout planContent(HeatingPlanSimulationService.SimulationResult result, SiteEntity site,
                                       List<SiteWeatherEntity> forecast, MarketSeries marketSeries) {
        VerticalLayout plan = new VerticalLayout();
        plan.setPadding(false);
        Details evidence = new Details("Inputs used to determine this plan",
                evidenceContent(result, site, forecast, marketSeries));
        evidence.setOpened(false);
        LocalDate today = LocalDate.now(ZONE);
        Map<LocalDate, List<HeatingPlanSimulationService.SimulationPoint>> byDate = new LinkedHashMap<>();
        result.points().forEach(point -> byDate.computeIfAbsent(point.time().atZone(ZONE).toLocalDate(), ignored -> new ArrayList<>()).add(point));
        VerticalLayout todayContent = dayContent(byDate.getOrDefault(today, List.of()), result, true);
        VerticalLayout tomorrowContent = dayContent(byDate.getOrDefault(today.plusDays(1), List.of()), result, false);
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
        plan.add(evidence, tabs, todayContent, tomorrowContent);
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

    private Grid<RoomOverview> roomOverviewGrid(List<RoomOverview> roomRows, List<DeviceEntity> thermostats,
                                                List<DeviceEntity> temperatureSensors) {
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

    private VerticalLayout dayContent(List<HeatingPlanSimulationService.SimulationPoint> points,
                                      HeatingPlanSimulationService.SimulationResult result, boolean today) {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        if (points.isEmpty()) {
            content.add(new Paragraph("Plan unavailable."));
            return content;
        }
        content.add(new HeatingPlanChart(points, ZONE));
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
        content.add(new H3("Planned actions"), actions);
        return content;
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

    private VerticalLayout evidenceContent(HeatingPlanSimulationService.SimulationResult result, SiteEntity site,
                                           List<SiteWeatherEntity> forecast, MarketSeries marketSeries) {
        VerticalLayout evidence = new VerticalLayout();
        evidence.setPadding(false);
        evidence.setSpacing(false);
        String siteText = site == null ? "not selected" : site.getName() + " (" + site.getTimezone() + ")";
        String weatherText = site == null ? "no site selected"
                : hasWeatherPlace(site) ? forecastSummary(forecast) : "site has no weather place configured";
        evidence.add(
                new Span("Plan generated: " + ZonedDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))),
                new Span("Planner status: " + (result.plannerActive() ? "active" : "inactive")
                        + " — " + result.plannerStatusReason()),
                new Span("Site: " + siteText),
                new Span("Market prices: " + marketSeries.description()),
                new Span("Weather: " + weatherText),
                new Span("Latest sensors: floor 22.0 °C, room 21.0 °C static placeholder, humidity 38% (mock, fresh)"),
                new Span("Comfort: room targets come from the saved room configuration; calculation uses their current average until per-room plans are wired"),
                new Span("Floor limits: normal 23.0 °C, preheat 27.0 °C, absolute maximum 29.0 °C"),
                new Span("Wood load: Normal basket, 8 kg; useful heat after 45 min, declining over 6 h"),
                new Span("Model: deterministic prototype v1; all thermal parameters are estimates"),
                new Span("Estimated electric use: " + result.energyKwh() + " kWh; cost: " + result.estimatedCostEur() + " €")
        );
        return evidence;
    }

    private HeatingPlanSimulationService.SimulationRequest simulationRequest(boolean stoveLoaded, LocalTime availableFrom,
                                                                             LocalTime availableTo, Double woodAmount,
                                                                             Double releaseDelayHours,
                                                                             Double releaseDurationHours,
                                                                             Double plannerWeatherThreshold,
                                                                             Double woodWeatherThreshold,
                                                                             BigDecimal targetRoomTemperature,
                                                                             List<HeatingPlanSimulationService.MarketPoint> market) {
        ZonedDateTime start = LocalDate.now(ZONE).atStartOfDay(ZONE);
        BigDecimal target = targetRoomTemperature == null ? new BigDecimal("21.00") : targetRoomTemperature;
        var settings = new HeatingPlanSimulationService.Settings(Duration.ofHours(1), Duration.ofHours(6),
                new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("23"), new BigDecimal("27"),
                new BigDecimal("29"), new BigDecimal("19"), target.subtract(BigDecimal.ONE),
                target.add(new BigDecimal("2.50")),
                BigDecimal.valueOf(plannerWeatherThreshold));
        var model = new HeatingPlanSimulationService.ThermalModel(new BigDecimal("2"), new BigDecimal("0.8"),
                new BigDecimal("0.06"), new BigDecimal("0.012"), new BigDecimal("0.001"));
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
        return new HeatingPlanSimulationService.SimulationRequest(new BigDecimal("22"), new BigDecimal("21"),
                settings, model, market, stove);
    }

    private MarketSeries marketSeries(com.nitramite.porssiohjain.entity.AccountEntity account, SiteEntity site,
                                      BigDecimal taxPercent, ElectricityContractEntity transferContract,
                                      List<SiteWeatherEntity> forecast) {
        ZonedDateTime start = LocalDate.now(ZONE).atStartOfDay(ZONE);
        ZonedDateTime end = start.plusDays(2);
        if (account == null) {
            return mockMarketSeries(start, forecast, "mock prices because account is unavailable");
        }
        String marketIndex = NordpoolMarket.normalize(account.getMarketIndexName());
        List<NordpoolEntity> prices = nordpoolRepository.findPricesBetween(marketIndex, start.toInstant(), end.toInstant());
        if (prices.isEmpty()) {
            return mockMarketSeries(start, forecast, "mock prices because Nordpool rows are missing for " + marketIndex);
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

    private MarketSeries mockMarketSeries(ZonedDateTime start, List<SiteWeatherEntity> forecast, String reason) {
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

    private record MarketSeries(List<HeatingPlanSimulationService.MarketPoint> points, String description) {
    }

    private record WeatherValues(BigDecimal temperature, BigDecimal windSpeedMs) {
    }

    private BigDecimal representativeTarget(List<RoomOverview> roomRows) {
        return roomRows.stream()
                .map(RoomOverview::targetRoomTemperature)
                .filter(value -> value != null)
                .reduce(BigDecimal::add)
                .map(sum -> sum.divide(BigDecimal.valueOf(roomRows.size()), 2, java.math.RoundingMode.HALF_UP))
                .orElse(new BigDecimal("21.00"));
    }

    private BigDecimal decimalOrDefault(Double value, String fallback) {
        return value == null ? new BigDecimal(fallback) : BigDecimal.valueOf(value);
    }

    private Integer minutesFromHours(Double value, int fallbackMinutes) {
        return value == null ? fallbackMinutes : Math.toIntExact(Math.round(value * 60));
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
                            thermostats.stream()
                                    .filter(device -> room.controllingDeviceId() != null
                                            && device.getId().equals(room.controllingDeviceId()))
                                    .findFirst()
                                    .orElse(null),
                            temperatureSensors.stream()
                                    .filter(device -> room.roomSensorDeviceId() != null
                                            && device.getId().equals(room.roomSensorDeviceId()))
                                    .findFirst()
                                    .orElse(null)
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
            return "No stored forecast rows for today and tomorrow yet; simulation falls back to mock weather.";
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

    private String deviceLabel(DeviceEntity device) {
        if (device == null) {
            return "";
        }
        return device.getDeviceName() + " · " + device.getDevicePlatform();
    }

    private static final class RoomOverview {
        private String room;
        private HeatingPlannerHeatSourceType heatSource;
        private BigDecimal targetRoomTemperature;
        private DeviceEntity controller;
        private DeviceEntity roomSensor;

        private RoomOverview(String room, HeatingPlannerHeatSourceType heatSource, BigDecimal targetRoomTemperature,
                             DeviceEntity controller, DeviceEntity roomSensor) {
            this.room = room;
            this.heatSource = heatSource;
            this.targetRoomTemperature = targetRoomTemperature == null ? new BigDecimal("21.00") : targetRoomTemperature;
            this.controller = controller;
            this.roomSensor = roomSensor;
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
    }
}
