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
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.SystemLogService;
import com.nitramite.porssiohjain.services.models.SystemLogResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.nitramite.porssiohjain.views.components.Divider.createDivider;

@PageTitle("Pörssiohjain - Admin")
@Route("admin")
@PermitAll
public class AdminView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final I18nService i18n;
    private final SystemLogService systemLogService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Europe/Helsinki"));
    private final VerticalLayout systemLogList = createLogList();
    private final VerticalLayout mqttLogList = createLogList();

    public AdminView(
            AuthService authService,
            I18nService i18n,
            SystemLogService systemLogService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.systemLogService = systemLogService;

        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null || !account.isAdmin()) {
            return;
        }

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

        Button backButton = new Button("← " + t("admin.back"), e -> UI.getCurrent().navigate(HomeView.class));
        Button provisioningButton = new Button(t("admin.provisioning.button"),
                e -> UI.getCurrent().navigate(AdminProvisioningView.class));
        provisioningButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button refreshLogsButton = new Button("Refresh logs", e -> refreshLogs());

        H1 title = new H1(t("admin.title"));
        title.getStyle().set("margin-bottom", "1em");
        H3 systemLogsTitle = new H3("System logs");
        H3 mqttLogsTitle = new H3("MQTT logs");

        refreshLogs();

        card.add(
                backButton,
                title,
                provisioningButton,
                refreshLogsButton,
                createDivider(),
                systemLogsTitle,
                systemLogList,
                mqttLogsTitle,
                mqttLogList
        );
        add(card);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.rerouteToHomeIfNotAdmin(event, authService)) {
            return;
        }
    }

    private void refreshLogs() {
        populateLogList(systemLogList, systemLogService.findLatest(), t("admin.systemLogEmpty"));
        populateLogList(mqttLogList, systemLogService.findLatestMqtt(), "No MQTT logs available.");
    }

    private VerticalLayout createLogList() {
        VerticalLayout logList = new VerticalLayout();
        logList.setWidth("100%");
        logList.setPadding(false);
        logList.setSpacing(false);
        logList.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        logList.getStyle().set("border-radius", "8px");
        logList.getStyle().set("background-color", "var(--lumo-base-color)");
        return logList;
    }

    private void populateLogList(
            VerticalLayout target,
            List<SystemLogResponse> logs,
            String emptyMessage
    ) {
        target.removeAll();
        if (logs.isEmpty()) {
            Span empty = new Span(emptyMessage);
            empty.getStyle().set("display", "block");
            empty.getStyle().set("padding", "12px");
            target.add(empty);
            return;
        }
        for (SystemLogResponse log : logs) {
            String formattedTime = formatter.format(log.getCreatedAt());
            Span line = new Span("[" + formattedTime + "] " + log.getMessage());
            line.getStyle().set("display", "block");
            line.getStyle().set("padding", "6px 12px");
            target.add(line);
        }
    }
}
