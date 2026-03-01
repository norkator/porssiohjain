package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.*;
import com.nitramite.porssiohjain.views.components.EnergyUsagePriceChart;
import com.nitramite.porssiohjain.views.components.PriceChart;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nitramite.porssiohjain.views.components.Divider.createDivider;

@JsModule("./js/apexcharts.min.js")
@PageTitle("Pörssiohjain - Dashboard")
@Route("dashboard")
@PermitAll
public class DashboardView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    protected final I18nService i18n;
    private final FingridService fingridService;
    private final PricePredictionService pricePredictionService;
    private final SiteService siteService;
    private final PowerLimitService powerLimitService;

    public DashboardView(
            AuthService authService,
            DeviceService deviceService,
            SystemLogService systemLogService,
            I18nService i18n,
            FingridService fingridService,
            PricePredictionService pricePredictionService,
            SiteService siteService,
            PowerLimitService powerLimitService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.fingridService = fingridService;
        this.pricePredictionService = pricePredictionService;
        this.siteService = siteService;
        this.powerLimitService = powerLimitService;

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
        card.addClassName("responsive-card");

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

        PriceChart energyForecastChart = new PriceChart();
        String chartTitle = t("dashboard.energyForecast");
        String nowLabel = t("controlTable.chart.now");
        String xAxisLabel = t("controlTable.chart.time");
        String windForecastLabel = t("dashboard.windForecast");
        String pricePredictionLabel = t("dashboard.pricePrediction");

        List<FingridWindForecastResponse> fingridWindForecastResponses = fingridService.getFingridWindForecastData();
        List<PricePredictionResponse> pricePredictionResponses = pricePredictionService.getFuturePredictions();

        Map<Instant, Double> priceByTime = pricePredictionResponses.stream()
                .collect(Collectors.toMap(
                        PricePredictionResponse::getTimestamp,
                        p -> p.getPriceCents().doubleValue()
                ));

        List<String> timestamps = new ArrayList<>();
        List<Double> windForecastValues = new ArrayList<>();
        List<Double> priceValues = new ArrayList<>();

        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter jsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(zone);

        List<Instant> priceTimes = new ArrayList<>(priceByTime.keySet());
        Collections.sort(priceTimes);

        for (FingridWindForecastResponse entry : fingridWindForecastResponses) {
            Instant tsInstant = entry.getStartTime();
            timestamps.add(jsFormatter.format(tsInstant));
            windForecastValues.add(entry.getValue().doubleValue());
            Instant lower = null, upper = null;
            for (Instant priceTime : priceTimes) {
                if (!priceTime.isAfter(tsInstant)) lower = priceTime;
                if (priceTime.isAfter(tsInstant)) {
                    upper = priceTime;
                    break;
                }
            }
            Double interpolatedPrice;
            if (lower == null) interpolatedPrice = priceByTime.get(upper);
            else if (upper == null) interpolatedPrice = priceByTime.get(lower);
            else {
                double p0 = priceByTime.get(lower);
                double p1 = priceByTime.get(upper);
                long t0 = lower.toEpochMilli();
                long t1 = upper.toEpochMilli();
                long t = tsInstant.toEpochMilli();
                interpolatedPrice = p0 + (p1 - p0) * ((double) (t - t0) / (t1 - t0));
            }
            priceValues.add(interpolatedPrice);
        }

        energyForecastChart.setData(
                timestamps,
                windForecastValues,
                priceValues,
                "MW",
                "c/kWh",
                xAxisLabel,
                chartTitle,
                nowLabel,
                windForecastLabel,
                pricePredictionLabel
        );

        H2 sitePowerUsage = new H2(t("dashboard.siteEnergyUsage"));

        List<SiteResponse> sites = siteService.getAllSites(getAccountId());
        ComboBox<SiteResponse> siteBox = new ComboBox<>(t("controlTable.field.site"));
        siteBox.setItems(sites);
        siteBox.setItemLabelGenerator(SiteResponse::getName);
        siteBox.setClearButtonVisible(true);

        Div siteContentContainer = new Div();
        siteContentContainer.setWidthFull();

        siteBox.addValueChangeListener(event -> {
            SiteResponse selectedSite = event.getValue();
            siteContentContainer.removeAll();
            if (selectedSite != null) {
                Component content = createSiteContent(selectedSite);
                siteContentContainer.add(content);
            }
        });
        if (sites.size() == 1) {
            siteBox.setValue(sites.getFirst());
        }

        card.add(
                backButton, title, createDivider(), deviceTitle, deviceLayout, createDivider(),
                energyForecastChart, createDivider(),
                sitePowerUsage, siteBox, siteContentContainer,
                createDivider(), logTitle, logList
        );
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
        name.getStyle().set("font-size", "1.2em");

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

    private Component createSiteContent(
            SiteResponse site
    ) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setWidthFull();
        List<PowerLimitResponse> sitePowerLimits = powerLimitService.getAllSiteLimits(getAccountId(), site.getId());
        ComboBox<PowerLimitResponse> limitBox = new ComboBox<>(t("dashboard.selectPowerLimit"));
        limitBox.setItems(sitePowerLimits);
        limitBox.setItemLabelGenerator(PowerLimitResponse::getName);
        limitBox.setWidth("300px");
        limitBox.setClearButtonVisible(true);
        Div limitContentContainer = new Div();
        limitContentContainer.setWidthFull();
        limitBox.addValueChangeListener(event -> {
            PowerLimitResponse selectedLimit = event.getValue();
            limitContentContainer.removeAll();
            if (selectedLimit != null) {
                Component limitContent = createLimitContent(selectedLimit);
                limitContentContainer.add(limitContent);
            }
        });
        if (sitePowerLimits.size() == 1) {
            limitBox.setValue(sitePowerLimits.getFirst());
        }
        layout.add(limitBox, limitContentContainer);
        return layout;
    }

    private Component createLimitContent(PowerLimitResponse limit) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setWidthFull();
        ZoneId zone = ZoneId.systemDefault();
        try {
            if (limit.getTimezone() != null) {
                zone = ZoneId.of(limit.getTimezone());
            }
        } catch (Exception ignored) {
        }
        DatePicker monthPicker = new DatePicker(t("dashboard.selectMonth"));
        monthPicker.setValue(LocalDate.now());
        monthPicker.setWidth("250px");
        VerticalLayout contentContainer = new VerticalLayout();
        contentContainer.setPadding(false);
        contentContainer.setSpacing(true);
        contentContainer.setWidthFull();
        ZoneId finalZone = zone;
        monthPicker.addValueChangeListener(event -> {
            LocalDate selectedDate = event.getValue();
            if (selectedDate != null) {
                YearMonth selectedMonth = YearMonth.from(selectedDate);
                refreshLimitContent(contentContainer, limit, selectedMonth, finalZone);
            }
        });

        YearMonth currentMonth = YearMonth.now();
        refreshLimitContent(contentContainer, limit, currentMonth, zone);

        layout.add(monthPicker, contentContainer);
        return layout;
    }

    private void refreshLimitContent(
            VerticalLayout container,
            PowerLimitResponse limit,
            YearMonth selectedMonth,
            ZoneId zone
    ) {
        container.removeAll();
        List<DailyUsageCostResponse> history =
                powerLimitService.getDailyUsageCostForMonth(
                        getAccountId(),
                        limit.getId(),
                        selectedMonth
                );
        if (history.isEmpty()) {
            container.add(new Paragraph(t("dashboard.noHistoryData")));
            return;
        }
        EnergyUsagePriceChart chart = new EnergyUsagePriceChart();
        List<String> timestamps = new ArrayList<>();
        List<Double> usageSeries = new ArrayList<>();
        List<Double> costSeries = new ArrayList<>();
        DateTimeFormatter jsFormatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(zone);
        for (int i = 0; i < history.size(); i++) {
            DailyUsageCostResponse h = history.get(i);
            timestamps.add(jsFormatter.format(
                    h.getDate().atStartOfDay(zone).toInstant()
            ));
            usageSeries.add(h.getTotalUsageKwh().doubleValue());
            costSeries.add(h.getTotalCostEur().doubleValue());
        }
        chart.setData(
                timestamps,
                usageSeries,
                costSeries,
                "kWh",
                "€",
                t("controlTable.chart.time"),
                "Title",
                t("controlTable.chart.now"),
                t("dashboard.chart.usage"),
                t("dashboard.chart.cost")
        );
        container.add(chart);
        BigDecimal totalCost = history.stream()
                .map(DailyUsageCostResponse::getTotalCostEur)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        container.add(createTotalCostSection(totalCost));
    }

    private Component createTotalCostSection(BigDecimal totalCost) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("padding", "14px")
                .set("border-radius", "12px")
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("text-align", "center");
        H2 title = new H2(t("dashboard.totalCost"));
        title.getStyle().set("margin", "0");
        Div totalCostDiv = new Div();
        totalCostDiv.setText(totalCost + "€");
        totalCostDiv.getStyle()
                .set("font-size", "2rem")
                .set("font-weight", "bold");
        wrapper.add(title, totalCostDiv);
        return wrapper;
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
