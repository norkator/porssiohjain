/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.PublicMeasurementService;
import com.nitramite.porssiohjain.views.components.DeviceMeasurementHistoryChart;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Standalone share page: deliberately outside the authenticated desktop layout. */
@Route("public-measurements/:pageId/:measurementId?")
@PageTitle("Pörssiohjain - Shared measurements")
@AnonymousAllowed
public class PublicMeasurementsView extends VerticalLayout implements BeforeEnterObserver {
    private final PublicMeasurementService service;
    private final I18nService i18n;
    private UUID pageId;
    private Long measurementId;
    private int days = 7;

    public PublicMeasurementsView(PublicMeasurementService service, I18nService i18n) {
        this.service = service;
        this.i18n = i18n;
        VaadinSession session = VaadinSession.getCurrent();
        Locale locale = session == null ? null : session.getAttribute(Locale.class);
        if (locale != null) UI.getCurrent().setLocale(locale);
        setWidth("calc(100% - 16px)");
        setMaxWidth("1600px");
        setPadding(false);
        setSpacing(false);
        setAlignItems(Alignment.STRETCH);
        getStyle().set("margin", "8px auto");
        addClassName("public-measurements-view");
    }

    public static String path(UUID pageId) {
        return "public-measurements/" + pageId;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        try {
            pageId = UUID.fromString(event.getRouteParameters().get("pageId").orElseThrow());
            String item = event.getRouteParameters().get("measurementId").orElse(null);
            measurementId = item == null ? null : Long.valueOf(item);
            if (measurementId != null && measurementId <= 0) throw new IllegalArgumentException();
            days = 7;
        } catch (RuntimeException exception) {
            pageId = null;
            measurementId = null;
        }
        render();
    }

    private void render() {
        removeAll();
        FlexLayout actions = new FlexLayout();
        actions.addClassName("public-measurements-actions");
        actions.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        actions.setAlignItems(Alignment.END);
        actions.add(new Button(t("measurements.refresh"), event -> render()),
                new Button("English", event -> changeLocale("en")),
                new Button("Suomeksi", event -> changeLocale("fi")));
        actions.getChildren().map(Button.class::cast)
                .forEach(button -> button.addThemeVariants(ButtonVariant.LUMO_SMALL));
        FlexLayout header = new FlexLayout(new H1(t("publicMeasurements.title")), actions);
        header.addClassName("public-measurements-header");
        header.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        add(header);
        if (pageId == null) {
            add(new Paragraph(t("publicMeasurements.unavailable")));
            return;
        }
        var result = service.publicPage(pageId, measurementId, measurementId == null ? 1 : days, Instant.now());
        if (result.isEmpty()) {
            add(new Paragraph(t("publicMeasurements.unavailable")));
            return;
        }
        var page = result.get();
        if (measurementId == null) {
            add(new Paragraph(t("publicMeasurements.overview")));
        } else {
            add(new Anchor(path(pageId), t("publicMeasurements.back")));
            Select<Integer> range = new Select<>();
            range.setLabel(t("publicMeasurements.range"));
            range.setItems(1, 3, 7);
            range.setItemLabelGenerator(value -> value == 1 ? t("publicMeasurements.day") : t("publicMeasurements.days", value));
            range.setValue(days);
            range.addValueChangeListener(event -> {
                days = event.getValue();
                render();
            });
            actions.add(range);
        }
        if (page.series().isEmpty()) {
            add(new Paragraph(t("publicMeasurements.empty")));
        }
        Div charts = new Div();
        charts.addClassName(measurementId == null ? "public-measurements-grid" : "public-measurements-detail");
        for (var series : page.series()) {
            VerticalLayout card = new VerticalLayout();
            card.setWidthFull();
            card.setPadding(false);
            card.setSpacing(false);
            card.setAlignItems(Alignment.STRETCH);
            card.addClassName("public-measurement-card");
            String type = t("measurements.type." + series.type().name());
            card.add(new H2(series.deviceName() + " · " + type));
            ZoneId zone = ZoneId.of(series.timezone());
            var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone);
            card.add(new Paragraph(t("publicMeasurements.period", formatter.format(page.from()),
                    formatter.format(page.to()), series.timezone())));
            card.add(DeviceMeasurementHistoryChart.publicHistory(series.readings().stream()
                            .map(row -> new DeviceMeasurementHistoryChart.HistoryPoint(row.observedAt(), row.value().doubleValue()))
                            .toList(), zone, type, series.unit(), t("measurements.chartTime"),
                    t("publicMeasurements.noReadings"), UI.getCurrent().getLocale().toLanguageTag(), page.from(), page.to(),
                    measurementId == null));
            if (series.includesCloudSnapshots()) card.add(new Paragraph(t("measurements.snapshotHint")));
            if (measurementId == null) {
                card.add(new Anchor(path(pageId) + "/" + series.id(), t("publicMeasurements.openWeek")));
            }
            charts.add(card);
        }
        add(charts);
    }

    private void changeLocale(String language) {
        Locale locale = Locale.of(language);
        UI.getCurrent().setLocale(locale);
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) session.setAttribute(Locale.class, locale);
        render();
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}
