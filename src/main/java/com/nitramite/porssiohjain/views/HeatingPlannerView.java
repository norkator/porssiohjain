/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService;
import com.nitramite.porssiohjain.views.components.HeatingPlanChart;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PageTitle("Pörssiohjain - Heating Planner")
@Route("heating-planner")
@PermitAll
public class HeatingPlannerView extends VerticalLayout implements BeforeEnterObserver {

    private static final ZoneId ZONE = ZoneId.of("Europe/Helsinki");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final AuthService authService;

    public HeatingPlannerView(AuthService authService, HeatingPlanSimulationService simulationService) {
        this.authService = authService;
        setSizeFull();
        setAlignItems(Alignment.CENTER);

        VerticalLayout card = new VerticalLayout();
        card.addClassName("responsive-card");
        card.setWidthFull();
        card.setMaxWidth("1200px");

        Button back = new Button("Back", VaadinIcon.ARROW_LEFT.create(), e -> getUI()
                .ifPresent(ui -> ui.navigate(HomeView.class)));
        H1 title = new H1("Heating Planner");
        title.getStyle().set("margin", "0");
        Span mockBadge = new Span("MOCK DATA · SIMULATION ONLY");
        mockBadge.getElement().getThemeList().add("badge warning");
        HorizontalLayout heading = new HorizontalLayout(title, mockBadge);
        heading.setAlignItems(Alignment.CENTER);

        Paragraph summary = new Paragraph("Whole-house plan · charge floor heating when electricity is cheap and recommend wood before expensive periods");

        Checkbox loaded = new Checkbox("Stove is loaded and ready", true);
        TimePicker availableFrom = new TimePicker("Available to light from", LocalTime.of(6, 0));
        TimePicker availableTo = new TimePicker("Available to light until", LocalTime.of(22, 0));
        NumberField woodAmount = numberField("Static wood load (kg)", 8, 1, 30);
        NumberField releaseDelay = numberField("Delay before useful heat (hours)", 0.75, 0, 12);
        NumberField releaseDuration = numberField("Heat release duration (hours)", 6, 0.25, 48);
        NumberField plannerWeatherThreshold = numberField("Planner active below (°C)", 5, -40, 20);
        NumberField woodWeatherThreshold = numberField("Recommend wood below (°C)", 0, -40, 20);
        FormLayout stoveForm = new FormLayout(loaded, availableFrom, availableTo, woodAmount, releaseDelay,
                releaseDuration, plannerWeatherThreshold, woodWeatherThreshold);
        stoveForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("650px", 3));
        Details stoveConfiguration = new Details("Wood stove availability and heat profile", stoveForm);
        stoveConfiguration.setOpened(true);

        Grid<RoomOverview> rooms = roomOverviewGrid();
        Details roomConfiguration = new Details("Rooms and heat sources", rooms);
        roomConfiguration.setOpened(true);

        VerticalLayout planHost = new VerticalLayout();
        planHost.setPadding(false);
        Runnable calculate = () -> {
            planHost.removeAll();
            var request = mockRequest(loaded.getValue(), availableFrom.getValue(), availableTo.getValue(),
                    woodAmount.getValue(), releaseDelay.getValue(), releaseDuration.getValue(),
                    plannerWeatherThreshold.getValue(), woodWeatherThreshold.getValue());
            planHost.add(planContent(simulationService.simulate(request)));
        };
        Button recalculate = new Button("Recalculate mock plan", VaadinIcon.REFRESH.create(), event -> calculate.run());
        recalculate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        calculate.run();

        card.add(back, heading, summary, stoveConfiguration, roomConfiguration, recalculate, planHost);
        add(card);
    }

    private VerticalLayout planContent(HeatingPlanSimulationService.SimulationResult result) {
        VerticalLayout plan = new VerticalLayout();
        plan.setPadding(false);
        Details evidence = new Details("Inputs used to determine this plan", evidenceContent(result));
        evidence.setOpened(true);
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

    private Grid<RoomOverview> roomOverviewGrid() {
        Grid<RoomOverview> grid = new Grid<>(RoomOverview.class, false);
        grid.addColumn(RoomOverview::room).setHeader("Room").setFlexGrow(1);
        grid.addColumn(RoomOverview::heating).setHeader("Heat source").setFlexGrow(2);
        grid.addColumn(RoomOverview::sensor).setHeader("Room sensor").setFlexGrow(2);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setAllRowsVisible(true);
        grid.setItems(
                new RoomOverview("Living room", "Wood stove", "Living-room temperature/humidity sensor"),
                new RoomOverview("Kitchen", "Floor thermostat", "Kitchen temperature/humidity sensor"),
                new RoomOverview("Shower", "Floor thermostat", "Shower temperature/humidity sensor"),
                new RoomOverview("Toilet", "Floor thermostat", "Toilet temperature/humidity sensor"),
                new RoomOverview("Entrance", "Floor thermostat", "Entrance temperature/humidity sensor")
        );
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

    private VerticalLayout evidenceContent(HeatingPlanSimulationService.SimulationResult result) {
        VerticalLayout evidence = new VerticalLayout();
        evidence.setPadding(false);
        evidence.setSpacing(false);
        evidence.add(
                new Span("Plan generated: " + ZonedDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))),
                new Span("Planner status: " + (result.plannerActive() ? "active" : "inactive")
                        + " — " + result.plannerStatusReason()),
                new Span("Market prices: mock hourly values for FI; cheap ≤ 5 c/kWh, expensive ≥ 20 c/kWh"),
                new Span("Weather: mock forecast, outdoor -12…-6 °C and wind 3…6 m/s"),
                new Span("Latest sensors: floor 22.0 °C, room 21.0 °C, humidity 38% (mock, fresh)"),
                new Span("Comfort: minimum 20.0 °C, target 21.0 °C, maximum 23.5 °C"),
                new Span("Floor limits: normal 23.0 °C, preheat 27.0 °C, absolute maximum 29.0 °C"),
                new Span("Wood load: Normal basket, 8 kg; useful heat after 45 min, declining over 6 h"),
                new Span("Model: deterministic prototype v1; all thermal parameters are estimates"),
                new Span("Estimated electric use: " + result.energyKwh() + " kWh; cost: " + result.estimatedCostEur() + " €")
        );
        return evidence;
    }

    private HeatingPlanSimulationService.SimulationRequest mockRequest(boolean stoveLoaded, LocalTime availableFrom,
                                                                       LocalTime availableTo, Double woodAmount,
                                                                       Double releaseDelayHours,
                                                                       Double releaseDurationHours,
                                                                       Double plannerWeatherThreshold,
                                                                       Double woodWeatherThreshold) {
        ZonedDateTime start = LocalDate.now(ZONE).atStartOfDay(ZONE);
        List<HeatingPlanSimulationService.MarketPoint> market = new ArrayList<>();
        for (int hour = 0; hour < 48; hour++) {
            int localHour = start.plusHours(hour).getHour();
            BigDecimal price = localHour >= 7 && localHour < 11 ? new BigDecimal("26.0")
                    : localHour >= 17 && localHour < 21 ? new BigDecimal("31.0")
                    : localHour >= 1 && localHour < 6 ? new BigDecimal("3.5") : new BigDecimal("11.0");
            BigDecimal outdoor = BigDecimal.valueOf(-12 + Math.min(localHour, 12) * 0.5);
            market.add(new HeatingPlanSimulationService.MarketPoint(start.plusHours(hour).toInstant(), price,
                    outdoor, localHour >= 12 ? new BigDecimal("6.0") : new BigDecimal("3.0")));
        }
        var settings = new HeatingPlanSimulationService.Settings(Duration.ofHours(1), Duration.ofHours(6),
                new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("23"), new BigDecimal("27"),
                new BigDecimal("29"), new BigDecimal("19"), new BigDecimal("20"), new BigDecimal("23.5"),
                BigDecimal.valueOf(plannerWeatherThreshold));
        var model = new HeatingPlanSimulationService.ThermalModel(new BigDecimal("2"), new BigDecimal("0.8"),
                new BigDecimal("0.06"), new BigDecimal("0.012"), new BigDecimal("0.001"));
        List<HeatingPlanSimulationService.StoveAvailability> availability = List.of(
                new HeatingPlanSimulationService.StoveAvailability(
                        start.with(availableFrom).toInstant(), start.with(availableTo).toInstant()),
                new HeatingPlanSimulationService.StoveAvailability(
                        start.plusDays(1).with(availableFrom).toInstant(), start.plusDays(1).with(availableTo).toInstant())
        );
        var stove = new HeatingPlanSimulationService.WoodStoveSettings(true, stoveLoaded, "Static wood load",
                BigDecimal.valueOf(woodAmount), Duration.ofMinutes(Math.round(releaseDelayHours * 60)),
                Duration.ofMinutes(Math.round(releaseDurationHours * 60)), new BigDecimal("0.35"),
                BigDecimal.valueOf(woodWeatherThreshold), availability);
        return new HeatingPlanSimulationService.SimulationRequest(new BigDecimal("22"), new BigDecimal("21"),
                settings, model, market, stove);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.findAuthenticatedAccount(authService) == null) {
            event.forwardTo(LoginView.class);
        }
    }

    private record PlanAction(String time, String action, String reason) {
    }

    private record RoomOverview(String room, String heating, String sensor) {
    }
}
