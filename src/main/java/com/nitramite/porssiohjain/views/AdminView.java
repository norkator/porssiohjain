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
import com.nitramite.porssiohjain.services.ServiceNoticeService;
import com.nitramite.porssiohjain.services.SystemLogService;
import com.nitramite.porssiohjain.services.models.SystemLogResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
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
@Route(value = "admin", layout = MainLayout.class)
@PermitAll
public class AdminView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final I18nService i18n;
    private final SystemLogService systemLogService;
    private final ServiceNoticeService serviceNoticeService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Europe/Helsinki"));
    private final VerticalLayout systemLogList = createLogList();
    private final VerticalLayout mqttLogList = createLogList();

    public AdminView(
            AuthService authService,
            I18nService i18n,
            SystemLogService systemLogService,
            ServiceNoticeService serviceNoticeService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.systemLogService = systemLogService;
        this.serviceNoticeService = serviceNoticeService;

        var account = ViewAuthUtils.findRealAuthenticatedAccount(authService);
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
        Button mqttRelayTestButton = new Button(t("admin.mqttRelayTest.button"),
                e -> UI.getCurrent().navigate(AdminMqttRelayTestView.class));
        mqttRelayTestButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button usersButton = new Button(t("admin.users.button"),
                e -> UI.getCurrent().navigate(AdminUsersView.class));
        usersButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button controlDeviceCallsButton = new Button("Client call monitor",
                e -> UI.getCurrent().navigate(AdminClientCallLogView.class));
        controlDeviceCallsButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button refreshLogsButton = new Button("Refresh logs", e -> refreshLogs());
        FlexLayout actions = new FlexLayout(
                provisioningButton,
                mqttRelayTestButton,
                usersButton,
                controlDeviceCallsButton,
                refreshLogsButton
        );
        actions.setWidthFull();
        actions.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "var(--lumo-space-s)");
        actions.getChildren().forEach(component ->
                component.getElement().getStyle().set("flex", "1 1 180px"));

        H1 title = new H1(t("admin.title"));
        title.getStyle().set("margin-bottom", "1em");
        H3 systemLogsTitle = new H3("System logs");
        H3 mqttLogsTitle = new H3("MQTT logs");

        refreshLogs();

        card.add(
                backButton,
                title,
                createServiceNoticeEditor(),
                actions,
                createDivider(),
                systemLogsTitle,
                systemLogList,
                mqttLogsTitle,
                mqttLogList
        );
        add(card);
    }

    private VerticalLayout createServiceNoticeEditor() {
        ServiceNoticeService.Configuration configuration = serviceNoticeService.getConfiguration();

        H3 title = new H3(t("admin.serviceNotice.title"));
        title.getStyle().set("margin", "0");
        Span description = new Span(t("admin.serviceNotice.description"));
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Checkbox active = new Checkbox(t("admin.serviceNotice.active"), configuration.active());
        TextArea finnishText = new TextArea(t("admin.serviceNotice.finnishText"));
        finnishText.setValue(configuration.finnishText());
        finnishText.setMaxLength(ServiceNoticeService.MAX_TEXT_LENGTH);
        finnishText.setWidthFull();
        finnishText.setMinHeight("120px");
        TextArea englishText = new TextArea(t("admin.serviceNotice.englishText"));
        englishText.setValue(configuration.englishText());
        englishText.setMaxLength(ServiceNoticeService.MAX_TEXT_LENGTH);
        englishText.setWidthFull();
        englishText.setMinHeight("120px");

        FormLayout fields = new FormLayout(finnishText, englishText);
        fields.setWidthFull();
        fields.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("700px", 2)
        );

        Button save = new Button(t("admin.serviceNotice.save"));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(event -> {
            try {
                serviceNoticeService.save(active.getValue(), finnishText.getValue(), englishText.getValue());
                Notification notification = Notification.show(t("admin.serviceNotice.saved"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (IllegalArgumentException exception) {
                Notification notification = Notification.show(t("admin.serviceNotice.saveFailed", exception.getMessage()));
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        VerticalLayout editor = new VerticalLayout(title, description, active, fields, save);
        editor.setWidthFull();
        editor.setPadding(true);
        editor.setSpacing(true);
        editor.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "8px")
                .set("background-color", "var(--lumo-contrast-5pct)");
        return editor;
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
