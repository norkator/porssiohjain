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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.MqttRelayTestService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import static com.nitramite.porssiohjain.views.components.Divider.createDivider;

@PageTitle("Pörssiohjain - MQTT Relay Tests")
@Route("admin/mqtt-relay-tests")
@PermitAll
public class AdminMqttRelayTestView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final I18nService i18n;
    private final DeviceService deviceService;
    private final MqttRelayTestService mqttRelayTestService;
    private final Grid<DeviceResponse> grid = new Grid<>(DeviceResponse.class, false);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Europe/Helsinki"));
    private AccountEntity account;

    public AdminMqttRelayTestView(
            AuthService authService,
            I18nService i18n,
            DeviceService deviceService,
            MqttRelayTestService mqttRelayTestService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.deviceService = deviceService;
        this.mqttRelayTestService = mqttRelayTestService;

        account = ViewAuthUtils.findAuthenticatedAccount(authService);
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
        H1 title = new H1(t("admin.mqttRelayTest.title"));
        Span description = new Span(t("admin.mqttRelayTest.description"));
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");

        configureGrid();
        refreshGrid();

        card.add(backButton, title, description, createDivider(), grid);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToHomeIfNotAdmin(event, authService);
    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.addColumn(DeviceResponse::getDeviceName).setHeader(t("admin.mqttRelayTest.device")).setAutoWidth(true);
        grid.addColumn(device -> device.getUuid() != null ? device.getUuid().toString() : "")
                .setHeader(t("admin.mqttRelayTest.uuid"))
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(device -> device.getDevicePlatform() != null ? device.getDevicePlatform().name() : "")
                .setHeader(t("admin.mqttRelayTest.platform"))
                .setAutoWidth(true);
        grid.addColumn(device -> {
            MqttRelayTestService.RelayTest test = mqttRelayTestService.getTest(account.getId(), device.getId());
            if (test == null) {
                return t("admin.mqttRelayTest.idle");
            }
            String nextState = test.nextStateOn()
                    ? t("device.mqttDebug.state.on")
                    : t("device.mqttDebug.state.off");
            return t(
                    "admin.mqttRelayTest.runningStatus",
                    test.channel(),
                    test.intervalSeconds(),
                    nextState,
                    formatter.format(test.nextRunAt())
            );
        }).setHeader(t("admin.mqttRelayTest.status")).setAutoWidth(true).setFlexGrow(1);
        grid.addComponentColumn(this::createActionButton)
                .setHeader(t("admin.provisioning.actions"))
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private void refreshGrid() {
        List<DeviceResponse> devices = deviceService.getAllDevices(account.getId()).stream()
                .filter(device -> device.getDeviceType() == DeviceType.STANDARD)
                .filter(device -> Boolean.TRUE.equals(device.getMqttOnline()))
                .filter(device -> !Boolean.TRUE.equals(device.getShared()))
                .sorted(Comparator.comparing(DeviceResponse::getDeviceName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        grid.setItems(devices);
    }

    private Button createActionButton(DeviceResponse device) {
        MqttRelayTestService.RelayTest test = mqttRelayTestService.getTest(account.getId(), device.getId());
        if (test == null) {
            Button startButton = new Button(t("admin.mqttRelayTest.start"), e -> openStartDialog(device));
            startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            return startButton;
        }
        Button stopButton = new Button(t("admin.mqttRelayTest.stop"), e -> stopTest(device));
        stopButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        return stopButton;
    }

    private void openStartDialog(DeviceResponse device) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("admin.mqttRelayTest.dialogTitle", device.getDeviceName()));
        dialog.setWidth("min(520px, 95vw)");

        ComboBox<Integer> channelField = new ComboBox<>(t("admin.mqttRelayTest.channel"));
        channelField.setItems(0, 1, 2, 3);
        channelField.setValue(0);
        channelField.setRequired(true);

        ComboBox<Integer> intervalField = new ComboBox<>(t("admin.mqttRelayTest.interval"));
        intervalField.setItems(MqttRelayTestService.SUPPORTED_INTERVAL_SECONDS);
        intervalField.setItemLabelGenerator(seconds -> t("admin.mqttRelayTest.seconds", seconds));
        intervalField.setValue(5);
        intervalField.setRequired(true);

        FormLayout form = new FormLayout(channelField, intervalField);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("520px", 2)
        );

        Button startButton = new Button(t("admin.mqttRelayTest.start"), e -> {
            try {
                mqttRelayTestService.startTest(device, channelField.getValue(), intervalField.getValue());
                dialog.close();
                refreshGrid();
                showSuccess(t("admin.mqttRelayTest.started", device.getDeviceName()));
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button(t("common.cancel"), e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, startButton);
        dialog.open();
    }

    private void stopTest(DeviceResponse device) {
        try {
            mqttRelayTestService.stopTest(account.getId(), device.getId());
            refreshGrid();
            showSuccess(t("admin.mqttRelayTest.stopped", device.getDeviceName()));
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}
