package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.FingridWindForecastResponse;
import com.nitramite.porssiohjain.services.models.SystemLogResponse;
import com.nitramite.porssiohjain.views.components.PriceChart;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import elemental.json.Json;
import elemental.json.JsonArray;
import jakarta.annotation.security.PermitAll;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@JsModule("./js/apexcharts.min.js")
@PageTitle("Pörssiohjain - Dashboard")
@Route("dashboard")
@PermitAll
public class DashboardView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    protected final I18nService i18n;
    private final FingridService fingridService;

    public DashboardView(
            AuthService authService,
            DeviceService deviceService,
            SystemLogService systemLogService,
            I18nService i18n,
            FingridService fingridService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.fingridService = fingridService;

        setWidthFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("display", "flex");
        getStyle().set("flex-direction", "column");
        getStyle().set("align-items", "center");
        getStyle().set("overflow", "auto");

        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        // card.setAlignItems(Alignment.STRETCH);
        card.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        card.getStyle().set("border-radius", "12px");
        card.getStyle().set("padding", "32px");
        card.getStyle().set("background-color", "var(--lumo-base-color)");

        H1 title = new H1(t("dashboard.title"));
        title.getStyle().set("margin-bottom", "1em");

        H2 deviceTitle = new H2(t("dashboard.devicesTitle"));
        deviceTitle.getStyle().set("margin-top", "0");

        FlexLayout deviceLayout = new FlexLayout();
        deviceLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        deviceLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        deviceLayout.getStyle().set("gap", "16px");

        List<DeviceResponse> devices = deviceService.getAllDevices(getAccountId());
        for (DeviceResponse device : devices) {
            deviceLayout.add(createDeviceCard(device));
        }

        H2 logTitle = new H2(t("dashboard.systemLogTitle"));

        VerticalLayout logList = new VerticalLayout();
        logList.setWidth("100%");
        logList.setPadding(false);
        logList.setSpacing(false);
        logList.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        logList.getStyle().set("border-radius", "8px");
        logList.getStyle().set("background-color", "var(--lumo-base-color)");

        ZoneId helsinkiZone = ZoneId.of("Europe/Helsinki");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(helsinkiZone);

        List<SystemLogResponse> logs = systemLogService.findLatest();
        for (SystemLogResponse log : logs) {
            String formattedTime = formatter.format(log.getCreatedAt());
            Span line = new Span("[" + formattedTime + "] " + log.getMessage());
            line.getStyle().set("display", "block");
            line.getStyle().set("padding", "6px 12px");
            logList.add(line);
        }

        Button backButton = new Button("← " + t("dashboard.back"), e -> UI.getCurrent().navigate(HomeView.class));

        PriceChart windForecast = new PriceChart();
        String windForecastChartTitle = t("dashboard.windForecast");
        String nowLabel = t("controlTable.chart.now");
        String xAxisLabel = t("controlTable.chart.time");

        List<FingridWindForecastResponse> fingridWindForecastResponses = fingridService.getFingridWindForecastData();

        List<String> timestamps = new ArrayList<>();
        List<Double> windForecastValues = new ArrayList<>();

        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter jsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(zone);

        for (FingridWindForecastResponse entry : fingridWindForecastResponses) {
            String ts = jsFormatter.format(entry.getStartTime());
            timestamps.add(ts);
            windForecastValues.add(entry.getValue().doubleValue());
        }

        JsonArray jsTimestamps = Json.createArray();
        for (int i = 0; i < timestamps.size(); i++) {
            jsTimestamps.set(i, timestamps.get(i));
        }

        JsonArray jsWindForecastValues = Json.createArray();
        for (int i = 0; i < windForecastValues.size(); i++) {
            jsWindForecastValues.set(i, windForecastValues.get(i));
        }

        windForecast.setData(
                jsTimestamps,
                jsWindForecastValues,
                "MW",
                xAxisLabel,
                "MW",
                windForecastChartTitle,
                nowLabel
        );

        card.add(backButton, title, createDivider(), deviceTitle, deviceLayout, createDivider(), windForecast, createDivider(), logTitle, logList);
        add(card);
    }

    private Component createDeviceCard(DeviceResponse device) {
        VerticalLayout card = new VerticalLayout();
        card.setWidth("200px");
        card.setPadding(true);
        card.setSpacing(false);
        card.setAlignItems(Alignment.CENTER);
        card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        card.getStyle().set("border-radius", "12px");
        card.getStyle().set("background-color", "var(--lumo-base-color)");
        card.getStyle().set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)");
        card.getStyle().set("transition", "transform 0.1s ease-in-out");
        card.getElement().addEventListener("mouseover", e -> card.getStyle().set("transform", "scale(1.03)"));
        card.getElement().addEventListener("mouseout", e -> card.getStyle().remove("transform"));

        H3 name = new H3(device.getDeviceName());
        name.getStyle().set("margin", "0");

        Div statusCircle = new Div();
        statusCircle.getStyle().set("width", "14px");
        statusCircle.getStyle().set("height", "14px");
        statusCircle.getStyle().set("border-radius", "50%");
        statusCircle.getStyle().set("margin-top", "8px");

        boolean online = isDeviceOnline(device.getLastCommunication());
        statusCircle.getStyle().set("background-color", online ? "green" : "red");

        card.add(name, statusCircle);
        return card;
    }

    private boolean isDeviceOnline(Instant lastCommunication) {
        if (lastCommunication == null) return false;
        Duration diff = Duration.between(lastCommunication, Instant.now());
        return diff.toMinutes() < 10;
    }

    private Div createDivider() {
        Div hr = new Div();
        hr.getStyle().set("width", "100%")
                .set("height", "1px")
                .set("background-color", "var(--lumo-contrast-20pct)")
                .set("margin", "1rem 0");
        return hr;
    }

    private Long getAccountId() {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        AccountEntity account = authService.authenticate(token);
        return account.getId();
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null || token.isBlank()) {
            event.forwardTo(LoginView.class);
        }
    }

}
