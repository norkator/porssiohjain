/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.FingridService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.NordpoolService;
import com.nitramite.porssiohjain.services.models.TodayPriceChartPointResponse;
import com.nitramite.porssiohjain.services.models.TodayPriceChartResponse;
import com.nitramite.porssiohjain.views.components.ForecastChart;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

@Route(value = "forecasts", layout = MainLayout.class)
@PageTitle("Pörssiohjain - Forecasts")
@PermitAll
@JsModule("./forecast-chart.js")
@Slf4j
public class ForecastsView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final I18nService i18n;
    private final NordpoolService nordpoolService;
    private final FingridService fingridService;
    private final ComboBox<String> timezone = new ComboBox<>();
    private final Div prices = new Div();
    private final Div wind = new Div();
    private boolean loaded;
    private boolean timezoneChosen;

    public ForecastsView(AuthService authService, I18nService i18n, NordpoolService nordpoolService,
                         FingridService fingridService) {
        this.authService = authService;
        this.i18n = i18n;
        this.nordpoolService = nordpoolService;
        this.fingridService = fingridService;
        addClassName("forecasts-view");
        setWidthFull();
        timezone.setLabel(t("forecasts.timezone"));
        timezone.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        timezone.setValue("Europe/Helsinki");
        timezone.setWidth("min(280px, 100%)");
        timezone.addValueChangeListener(event -> {
            if (event.getValue() == null) {
                timezone.setValue(event.getOldValue());
                return;
            }
            if (event.isFromClient()) timezoneChosen = true;
            if (loaded) refresh();
        });
        Button refresh = new Button(t("forecasts.refresh"), event -> refresh());
        FlexLayout toolbar = new FlexLayout(timezone, refresh);
        toolbar.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        toolbar.setAlignItems(Alignment.END);
        toolbar.addClassName("forecasts-toolbar");
        prices.addClassName("forecast-card");
        wind.addClassName("forecast-card");
        add(new H1(t("home.forecasts")), toolbar, prices, wind);
        getElement().addEventListener("forecast-refresh", event -> refresh());
        addAttachListener(event -> {
            var details = event.getUI().getPage().getExtendedClientDetails();
            if (details.getTimeZoneId() != null) applyBrowserTimezone(details.getTimeZoneId());
            else details.refresh(updated -> applyBrowserTimezone(updated.getTimeZoneId()));
            getElement().executeJs("window.initForecastsView(this)");
        });
    }

    private void applyBrowserTimezone(String zone) {
        if (!timezoneChosen && zone != null && ZoneId.getAvailableZoneIds().contains(zone)) timezone.setValue(zone);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, authService)) return;
        loaded = true;
        refresh();
    }

    private void refresh() {
        if (ViewAuthUtils.findAuthenticatedAccount(authService) == null) return;
        renderPrices();
        renderWind();
    }

    private void renderPrices() {
        prices.removeAll();
        prices.add(new H2(t("forecasts.prices.title")), new Paragraph(t("forecasts.prices.description", timezone.getValue())));
        List<TodayPriceChartResponse> charts = new ArrayList<>();
        boolean failed = false;
        // Match hybrid-web's public market chart: default market and VAT, with browser-local days.
        for (int day = 0; day < 2; day++) {
            try {
                charts.add(day == 0 ? nordpoolService.getTodayChart(null, timezone.getValue(), null)
                        : nordpoolService.getTomorrowChart(null, timezone.getValue(), null));
            } catch (RuntimeException exception) {
                log.warn("Could not load Nord Pool forecast for day {}", day, exception);
                failed = true;
            }
        }
        List<TodayPriceChartPointResponse> points = mergePrices(charts);
        if (points.isEmpty()) {
            prices.add(new Paragraph(t(failed ? "forecasts.prices.failed" : "forecasts.prices.empty")));
            return;
        }
        if (failed) prices.add(new Paragraph(t("forecasts.prices.partial")));
        else if (charts.size() == 2 && charts.get(1).getPoints().isEmpty()) {
            prices.add(new Paragraph(t("forecasts.prices.tomorrowPending")));
        }
        List<BigDecimal> values = points.stream().map(TodayPriceChartPointResponse::getPrice).toList();
        Instant end = points.getLast().getTimestamp().plusSeconds(15 * 60);
        Instant now = Instant.now();
        BigDecimal current = points.stream().filter(point -> !point.getTimestamp().isAfter(now)
                        && point.getTimestamp().plusSeconds(15 * 60).isAfter(now))
                .map(TodayPriceChartPointResponse::getPrice).findFirst().orElse(null);
        Span currentValue = new Span(number(current, 2));
        currentValue.getElement().setAttribute("data-forecast-current", "");
        Div stats = new Div(metric("forecasts.current", currentValue, t("forecasts.priceUnit")),
                metric("forecasts.minimum", values.stream().min(BigDecimal::compareTo).orElseThrow(), t("forecasts.priceUnit"), 2),
                metric("forecasts.average", values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP), t("forecasts.priceUnit"), 2),
                metric("forecasts.maximum", values.stream().max(BigDecimal::compareTo).orElseThrow(), t("forecasts.priceUnit"), 2));
        stats.addClassName("forecast-stats");
        prices.add(stats, new ForecastChart(points.stream().map(TodayPriceChartPointResponse::getTimestamp).toList(),
                values, end, zone(), locale(), t("forecasts.prices.title"), t("forecasts.priceAxis"),
                t("forecasts.now"), true));
    }

    private void renderWind() {
        wind.removeAll();
        wind.add(new H2(t("forecasts.wind.title")), new Paragraph(t("forecasts.wind.description", timezone.getValue())));
        try {
            var forecast = fingridService.getWindForecastChart(timezone.getValue());
            if (forecast.getPoints().isEmpty()) {
                wind.add(new Paragraph(t("forecasts.wind.empty")));
                return;
            }
            Div stats = new Div(metric("forecasts.wind.todayAverage", forecast.getTodayAverage(), "MW", 0),
                    metric("forecasts.wind.tomorrowAverage", forecast.getTomorrowAverage(), "MW", 0),
                    metric("forecasts.wind.drop", forecast.getTomorrowDropPercent(), "%", 1));
            stats.addClassName("forecast-stats");
            var points = forecast.getPoints();
            Instant end = points.getLast().getEndTime();
            wind.add(stats, new ForecastChart(points.stream().map(point -> point.getStartTime()).toList(),
                    points.stream().map(point -> point.getMegawatts()).toList(), end, zone(), locale(),
                    t("forecasts.wind.title"), "MW", t("forecasts.now"), false),
                    new Paragraph(t("forecasts.wind.until", DateTimeFormatter.ofPattern("EEE d.M. HH:mm", locale())
                            .format(end.atZone(zone())))));
        } catch (RuntimeException exception) {
            log.warn("Could not load wind-power forecast", exception);
            wind.add(new Paragraph(t("forecasts.wind.failed")));
        }
    }

    static List<TodayPriceChartPointResponse> mergePrices(List<TodayPriceChartResponse> charts) {
        var byTime = new TreeMap<Instant, TodayPriceChartPointResponse>();
        charts.forEach(chart -> chart.getPoints().forEach(point -> byTime.put(point.getTimestamp(), point)));
        return List.copyOf(byTime.values());
    }

    private Div metric(String key, BigDecimal value, String unit, int decimals) {
        return metric(key, new Span(number(value, decimals)), unit);
    }

    private Div metric(String key, Span value, String unit) {
        value.addClassName("forecast-stat-value");
        Div metric = new Div(new Span(t(key)), value, new Span(unit));
        metric.addClassName("forecast-stat");
        return metric;
    }

    private String number(BigDecimal value, int decimals) {
        if (value == null) return "–";
        NumberFormat format = NumberFormat.getNumberInstance(locale());
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        return format.format(value);
    }

    private Locale locale() { return UI.getCurrent() == null ? Locale.ENGLISH : UI.getCurrent().getLocale(); }
    private ZoneId zone() { return ZoneId.of(timezone.getValue()); }
    private String t(String key, Object... args) { return i18n.t(key, args); }
}
