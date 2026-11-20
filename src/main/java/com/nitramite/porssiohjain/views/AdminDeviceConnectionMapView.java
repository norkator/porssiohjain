/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AdminDeviceConnectionMapService;
import com.nitramite.porssiohjain.services.AdminDeviceConnectionMapService.MapClient;
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

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@PageTitle("Pörssiohjain - Device Connection Map")
@Route(value = "admin/device-connection-map", layout = MainLayout.class)
@PermitAll
@JsModule("./client-call-monitor.js")
public class AdminDeviceConnectionMapView extends VerticalLayout implements BeforeEnterObserver {
    private final AuthService authService;
    private final I18nService i18n;
    private final AdminDeviceConnectionMapService connectionMapService;
    private final DeviceConnectionMap map;
    private final Span refreshStatus = new Span();
    private boolean testMode;

    public AdminDeviceConnectionMapView(AuthService authService, I18nService i18n,
                                       AdminDeviceConnectionMapService connectionMapService) {
        this.authService = authService;
        this.i18n = i18n;
        this.connectionMapService = connectionMapService;
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
        Span testNotice = new Span(i18n.t("admin.connectionMap.testNotice"));
        testNotice.addClassName("connection-map-note");
        testNotice.setVisible(false);
        Button testData = new Button(i18n.t("admin.connectionMap.showTestData"));
        testData.addClickListener(event -> {
            var currentAccount = ViewAuthUtils.findAuthenticatedAccount(authService);
            if (currentAccount == null || !currentAccount.isAdmin()) return;
            testMode = !testMode;
            testData.setText(i18n.t(testMode ? "admin.connectionMap.showLiveData" : "admin.connectionMap.showTestData"));
            testData.getElement().setAttribute("aria-pressed", Boolean.toString(testMode));
            testNotice.setVisible(testMode);
            refreshMap();
        });
        testData.getElement().setAttribute("aria-pressed", "false");
        HorizontalLayout actions = new HorizontalLayout(monitor, testData, refreshStatus);
        actions.setAlignItems(Alignment.CENTER);
        actions.getStyle().set("flex-wrap", "wrap");
        refreshStatus.getElement().addEventListener("client-call-refresh", event -> {
            refreshMap();
            refreshStatus.getElement().executeJs("this.clientCallRefreshComplete?.()");
        });
        refreshStatus.addAttachListener(event -> refreshStatus.getElement().executeJs(
                "window.initClientCallMonitor(this, $0, $1)",
                i18n.t("admin.clientCalls.nextRefresh", "{seconds}"), i18n.t("admin.clientCalls.refreshing")));
        card.add(new H1(i18n.t("admin.connectionMap.title")), description, retention, actions, testNotice, map);
        add(card);
        refreshMap();
    }

    private void refreshMap() {
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account != null && account.isAdmin()) {
            map.update(testMode ? testClients() : connectionMapService.findClients());
        }
    }

    private List<MapClient> testClients() {
        Instant now = Instant.now();
        return List.of(
                testClient("HEAT_PUMP", "heatPump", 1001L, true, "API", now.minusSeconds(90), null),
                testClient("CONTROL_DEVICE", "controlDevice", 1001L, true, "API + MQTT", now.minusSeconds(15), "192.0.2.10"),
                testClient("THERMOSTAT", "thermostat", 1001L, true, "MQTT", now.minusSeconds(30), null),
                testClient("TEMPERATURE_SENSOR", "temperatureSensor", 1001L, true, "MQTT", now.minusSeconds(45), null),
                testClient("HEAT_PUMP", "heatPump", 1002L, true, "API", now.minusSeconds(300), null),
                testClient("ZIGBEE_GATEWAY", "zigbeeGateway", 1002L, true, "API", now.minusSeconds(60), "198.51.100.20"),
                testClient("TEMPERATURE_SENSOR", "temperatureSensor", 1002L, true, "MQTT", now.minusSeconds(15), null),
                testClient("CONTROL_DEVICE", "controlDevice", 1002L, false, "", now.minusSeconds(1800), "198.51.100.20"));
    }

    private MapClient testClient(String type, String labelKey, long accountId, boolean online,
                                 String connection, Instant lastContact, String ip) {
        boolean hasCall = ip != null;
        return new MapClient(type, "test-" + accountId + "-" + type.toLowerCase(Locale.ROOT),
                i18n.t("admin.connectionMap.testDeviceName", i18n.t("admin.connectionMap." + labelKey)),
                accountId, "test" + (accountId - 1000) + "@example.invalid", online, connection, lastContact,
                hasCall ? lastContact : null, ip,
                !hasCall ? null : type.equals("ZIGBEE_GATEWAY") ? "/zigbee-gateways/{gatewayId}/sync" : "/control/{deviceUuid}");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToHomeIfNotAdmin(event, authService);
    }
}
