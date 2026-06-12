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

import com.nitramite.porssiohjain.entity.enums.DeviceChipId;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.FactoryDeviceStatus;
import com.nitramite.porssiohjain.entity.enums.FactoryTestStatus;
import com.nitramite.porssiohjain.entity.enums.MqttDeviceProfile;
import com.nitramite.porssiohjain.services.models.CreateFactoryTestRunRequest;
import com.nitramite.porssiohjain.services.models.CreateFactoryTestStepRequest;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.FactoryProvisioningService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.CreateFactoryDeviceRequest;
import com.nitramite.porssiohjain.services.models.FactoryDeviceResponse;
import com.nitramite.porssiohjain.services.models.FactoryTestRunResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import static com.nitramite.porssiohjain.views.components.Divider.createDivider;

@PageTitle("Pörssiohjain - Provisioning")
@Route("admin/provisioning")
@PermitAll
public class AdminProvisioningView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final I18nService i18n;
    private final FactoryProvisioningService factoryProvisioningService;
    private Long adminAccountId;

    private final Grid<FactoryDeviceResponse> grid = new Grid<>(FactoryDeviceResponse.class, false);
    private final TextField serialNumberField = new TextField();
    private final ComboBox<DeviceChipId> chipIdField = new ComboBox<>();
    private final ComboBox<DevicePlatform> platformField = new ComboBox<>();
    private final ComboBox<MqttDeviceProfile> profileField = new ComboBox<>();

    public AdminProvisioningView(
            AuthService authService,
            I18nService i18n,
            FactoryProvisioningService factoryProvisioningService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.factoryProvisioningService = factoryProvisioningService;

        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null || !account.isAdmin()) {
            return;
        }

        this.adminAccountId = account.getId();

        setWidthFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.STRETCH);

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.addClassName("responsive-card");

        Button backButton = new Button("← " + t("admin.back"), e -> UI.getCurrent().navigate(AdminView.class));
        H1 title = new H1(t("admin.provisioning.title"));
        H2 createTitle = new H2(t("admin.provisioning.createTitle"));
        H2 listTitle = new H2(t("admin.provisioning.listTitle"));

        configureForm();
        configureGrid();
        refreshGrid();

        card.add(backButton, title, createDivider(), createTitle, createFormLayout(), createDivider(), listTitle, grid);
        add(card);
    }

    private FormLayout createFormLayout() {
        Button createButton = new Button(t("admin.provisioning.createButton"), e -> createFactoryDevice());
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button clearButton = new Button(t("admin.provisioning.clearButton"), e -> clearForm());

        HorizontalLayout actions = new HorizontalLayout(createButton, clearButton);
        actions.setPadding(false);
        actions.setSpacing(true);

        FormLayout formLayout = new FormLayout(
                serialNumberField,
                platformField,
                profileField,
                chipIdField,
                actions
        );
        formLayout.setWidthFull();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("720px", 2)
        );
        formLayout.setColspan(actions, 2);
        return formLayout;
    }

    private void configureForm() {
        serialNumberField.setLabel(t("admin.provisioning.serial"));
        serialNumberField.setRequired(true);

        chipIdField.setLabel(t("admin.provisioning.chipId"));
        chipIdField.setItems(DeviceChipId.values());
        chipIdField.setClearButtonVisible(true);
        platformField.setLabel(t("admin.provisioning.platform"));
        platformField.setItems(DevicePlatform.values());
        platformField.setRequired(true);

        profileField.setLabel(t("admin.provisioning.profile"));
        profileField.setItems(MqttDeviceProfile.values());
        profileField.setValue(MqttDeviceProfile.GENERIC_RELAY);

    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.addColumn(FactoryDeviceResponse::getSerialNumber).setHeader(t("admin.provisioning.serial")).setAutoWidth(true);
        grid.addColumn(item -> item.getPlatform() != null ? item.getPlatform().name() : "")
                .setHeader(t("admin.provisioning.platform")).setAutoWidth(true);
        grid.addColumn(item -> item.getMqttDeviceProfile() != null ? item.getMqttDeviceProfile().name() : "")
                .setHeader(t("admin.provisioning.profile")).setAutoWidth(true);
        grid.addColumn(FactoryDeviceResponse::getClaimCode).setHeader(t("admin.provisioning.claimCode")).setAutoWidth(true);
        grid.addColumn(item -> item.getStatus() != null ? item.getStatus().name() : "")
                .setHeader(t("admin.provisioning.status")).setAutoWidth(true);
        grid.addColumn(item -> item.getMqttTopicRoot() != null ? item.getMqttTopicRoot() : "")
                .setHeader(t("admin.provisioning.topicRoot")).setAutoWidth(true).setFlexGrow(1);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Europe/Helsinki"));
        grid.addColumn(item -> item.getLastSeenAt() != null ? formatter.format(item.getLastSeenAt()) : "")
                .setHeader(t("admin.provisioning.lastSeen")).setAutoWidth(true);
        grid.addColumn(item -> latestRunLabel(item, formatter))
                .setHeader(t("admin.provisioning.latestTest")).setAutoWidth(true);
        grid.addComponentColumn(this::createActionButtons)
                .setHeader(t("admin.provisioning.actions"))
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private void refreshGrid() {
        List<FactoryDeviceResponse> items = factoryProvisioningService.listFactoryDevices();
        grid.setItems(items);
    }

    private void createFactoryDevice() {
        try {
            CreateFactoryDeviceRequest request = new CreateFactoryDeviceRequest();
            request.setSerialNumber(serialNumberField.getValue());
            request.setPlatform(platformField.getValue());
            request.setMqttDeviceProfile(profileField.getValue());
            request.setChipId(chipIdField.getValue());
            FactoryDeviceResponse created = factoryProvisioningService.createFactoryDevice(request);
            refreshGrid();
            clearForm();

            Notification notification = Notification.show(
                    t("admin.provisioning.created", created.getSerialNumber(), created.getClaimCode())
            );
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            Notification notification = Notification.show(e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private HorizontalLayout createActionButtons(FactoryDeviceResponse device) {
        Button startTestButton = new Button(t("admin.provisioning.mockStart"), e -> startMockTestRun(device));
        Button passButton = new Button(t("admin.provisioning.mockPass"), e -> finishMockTestRun(device, FactoryTestStatus.PASSED));
        Button failButton = new Button(t("admin.provisioning.mockFail"), e -> finishMockTestRun(device, FactoryTestStatus.FAILED));

        startTestButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        passButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
        failButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        startTestButton.setEnabled(device.getStatus() == FactoryDeviceStatus.REGISTERED
                || device.getStatus() == FactoryDeviceStatus.FAILED);
        passButton.setEnabled(device.getStatus() == FactoryDeviceStatus.TESTING
                || device.getStatus() == FactoryDeviceStatus.REGISTERED);
        failButton.setEnabled(device.getStatus() == FactoryDeviceStatus.TESTING
                || device.getStatus() == FactoryDeviceStatus.REGISTERED);

        HorizontalLayout layout = new HorizontalLayout(startTestButton, passButton, failButton);
        layout.setPadding(false);
        layout.setSpacing(true);
        return layout;
    }

    private void startMockTestRun(FactoryDeviceResponse device) {
        try {
            CreateFactoryTestRunRequest request = new CreateFactoryTestRunRequest();
            request.setStationName("vaadin-admin");
            request.setNotes("Mock test run started from admin provisioning UI");
            factoryProvisioningService.startTestRun(adminAccountId, device.getId(), request);
            refreshGrid();
            showSuccess(t("admin.provisioning.mockStarted", device.getSerialNumber()));
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void finishMockTestRun(FactoryDeviceResponse device, FactoryTestStatus finalStatus) {
        try {
            FactoryDeviceResponse refreshed = factoryProvisioningService.getFactoryDevice(device.getId());
            FactoryTestRunResponse latestRun = latestRun(refreshed);
            if (latestRun == null || latestRun.getStatus() != FactoryTestStatus.RUNNING) {
                CreateFactoryTestRunRequest runRequest = new CreateFactoryTestRunRequest();
                runRequest.setStationName("vaadin-admin");
                runRequest.setNotes("Auto-created mock test run from admin provisioning UI");
                latestRun = factoryProvisioningService.startTestRun(adminAccountId, device.getId(), runRequest);
            }

            CreateFactoryTestStepRequest stepRequest = new CreateFactoryTestStepRequest();
            stepRequest.setStepKey("mock_final_result");
            stepRequest.setStatus(finalStatus);
            stepRequest.setExpectedValue(finalStatus.name());
            stepRequest.setActualValue(finalStatus.name());
            stepRequest.setDetails(finalStatus == FactoryTestStatus.PASSED
                    ? "Mock pass from admin provisioning UI"
                    : "Mock fail from admin provisioning UI");
            stepRequest.setFinalizeRun(true);
            factoryProvisioningService.addTestStep(latestRun.getId(), stepRequest);

            refreshGrid();
            showSuccess(t(
                    finalStatus == FactoryTestStatus.PASSED
                            ? "admin.provisioning.mockPassed"
                            : "admin.provisioning.mockFailed",
                    device.getSerialNumber()
            ));
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void clearForm() {
        serialNumberField.clear();
        chipIdField.clear();
        platformField.clear();
        profileField.setValue(MqttDeviceProfile.GENERIC_RELAY);
    }

    private FactoryTestRunResponse latestRun(FactoryDeviceResponse device) {
        if (device.getTestRuns() == null || device.getTestRuns().isEmpty()) {
            return null;
        }
        return device.getTestRuns().getFirst();
    }

    private String latestRunLabel(FactoryDeviceResponse device, DateTimeFormatter formatter) {
        FactoryTestRunResponse latestRun = latestRun(device);
        if (latestRun == null) {
            return "";
        }
        String finished = latestRun.getFinishedAt() != null ? formatter.format(latestRun.getFinishedAt()) : t("admin.provisioning.running");
        return latestRun.getStatus() + " / " + finished;
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(Objects.requireNonNullElse(message, t("admin.provisioning.genericError")));
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToHomeIfNotAdmin(event, authService);
    }
}
