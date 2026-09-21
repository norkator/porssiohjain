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

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.AdminClientCallLogService;
import com.nitramite.porssiohjain.services.AdminClientCallLogService.ClientType;
import com.nitramite.porssiohjain.services.AdminClientCallLogService.DeviceCallLog;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.PermitAll;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.nitramite.porssiohjain.views.components.Divider.createDivider;

@PageTitle("Pörssiohjain - Client Call Monitor")
@Route(value = "admin/client-call-monitor", layout = MainLayout.class)
@PermitAll
public class AdminClientCallLogView extends VerticalLayout implements BeforeEnterObserver {

    private static final Duration FRESH_LOG_AGE = Duration.ofMinutes(2);

    private final AuthService authService;
    private final I18nService i18n;
    private final AdminClientCallLogService deviceCallLogService;
    private final Grid<DeviceCallLog> grid = new Grid<>(DeviceCallLog.class, false);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Europe/Helsinki"));
    private Registration pollRegistration;

    public AdminClientCallLogView(
            AuthService authService,
            I18nService i18n,
            AdminClientCallLogService deviceCallLogService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.deviceCallLogService = deviceCallLogService;

        var account = ViewAuthUtils.findRealAuthenticatedAccount(authService);
        if (account == null || !account.isAdmin()) {
            return;
        }

        setWidthFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.STRETCH);

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.addClassName("responsive-card");

        Button backButton = new Button("← " + t("admin.back"), e -> UI.getCurrent().navigate(AdminView.class));
        H1 title = new H1("Device and gateway calls");
        Span description = new Span("Latest in-memory control-device and Zigbee gateway calls. The list keeps up to 30 rows and refreshes every 10 seconds.");
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");

        configureGrid();
        refreshGrid();
        enablePolling();

        card.add(backButton, title, description, createDivider(), grid);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToHomeIfNotAdmin(event, authService);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (pollRegistration != null) {
            pollRegistration.remove();
            pollRegistration = null;
        }
        UI ui = detachEvent.getUI();
        if (ui != null) {
            ui.setPollInterval(-1);
        }
        super.onDetach(detachEvent);
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.addColumn(log -> formatter.format(log.calledAt()))
                .setHeader("Time")
                .setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(this::createTypeBadge))
                .setHeader("Type")
                .setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(DeviceCallLog::clientId)
                .setHeader("Client ID")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(DeviceCallLog::clientIp)
                .setHeader("Client IP")
                .setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(DeviceCallLog::endpoint)
                .setHeader("Endpoint")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.setPartNameGenerator(log -> isFresh(log) ? "fresh-control-device-call" : null);
    }

    private void refreshGrid() {
        grid.setItems(deviceCallLogService.findLatest());
    }

    private void enablePolling() {
        UI ui = UI.getCurrent();
        ui.setPollInterval(10_000);
        pollRegistration = ui.addPollListener(event -> refreshGrid());
    }

    private boolean isFresh(DeviceCallLog log) {
        return log.calledAt().isAfter(Instant.now().minus(FRESH_LOG_AGE));
    }

    private Span createTypeBadge(DeviceCallLog log) {
        Span badge = new Span(log.clientType().getLabel());
        badge.getElement().getThemeList().add("badge");
        if (log.clientType() == ClientType.ZIGBEE_GATEWAY) {
            badge.getElement().getThemeList().add("contrast");
        } else {
            badge.getElement().getThemeList().add("primary");
        }
        return badge;
    }
}
