/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AdminClientCallLogService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.views.components.DeviceConnectionMap;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@PageTitle("Pörssiohjain - Device Connection Map")
@Route(value = "admin/device-connection-map", layout = MainLayout.class)
@PermitAll
@JsModule("./client-call-monitor.js")
public class AdminDeviceConnectionMapView extends VerticalLayout implements BeforeEnterObserver {
    private final AuthService authService;
    private final AdminClientCallLogService callLogService;
    private final DeviceConnectionMap map;
    private final Span refreshStatus = new Span();

    public AdminDeviceConnectionMapView(AuthService authService, I18nService i18n,
                                       AdminClientCallLogService callLogService) {
        this.authService = authService;
        this.callLogService = callLogService;
        map = new DeviceConnectionMap(i18n);

        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null || !account.isAdmin()) return;

        setWidthFull();
        setAlignItems(Alignment.STRETCH);
        addClassName("connection-map-view");
        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");
        Span description = new Span(i18n.t("admin.connectionMap.description"));
        Span retention = new Span(i18n.t("admin.connectionMap.retention"));
        retention.addClassName("connection-map-note");
        Button monitor = new Button(i18n.t("admin.clientCalls.title"),
                event -> UI.getCurrent().navigate(AdminClientCallLogView.class));
        HorizontalLayout actions = new HorizontalLayout(monitor, refreshStatus);
        actions.setAlignItems(Alignment.CENTER);
        actions.getStyle().set("flex-wrap", "wrap");
        refreshStatus.getElement().addEventListener("client-call-refresh", event -> {
            refreshMap();
            refreshStatus.getElement().executeJs("this.clientCallRefreshComplete?.()");
        });
        refreshStatus.addAttachListener(event -> refreshStatus.getElement().executeJs(
                "window.initClientCallMonitor(this, $0, $1)",
                i18n.t("admin.clientCalls.nextRefresh", "{seconds}"), i18n.t("admin.clientCalls.refreshing")));
        card.add(new H1(i18n.t("admin.connectionMap.title")), description, retention, actions, map);
        add(card);
        refreshMap();
    }

    private void refreshMap() {
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account != null && account.isAdmin()) {
            map.update(callLogService.findLatestClients());
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToHomeIfNotAdmin(event, authService);
    }
}
