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

import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.MqttDeviceProfile;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.FactoryProvisioningService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.CreateFactoryDeviceRequest;
import com.nitramite.porssiohjain.services.models.FactoryDeviceResponse;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.nitramite.porssiohjain.views.components.Divider.createDivider;

@PageTitle("Pörssiohjain - Provisioning")
@Route("admin/provisioning")
@PermitAll
public class AdminProvisioningView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final I18nService i18n;
    private final FactoryProvisioningService factoryProvisioningService;

    private final Grid<FactoryDeviceResponse> grid = new Grid<>(FactoryDeviceResponse.class, false);
    private final TextField serialNumberField = new TextField();
    private final TextField productModelField = new TextField();
    private final TextField firmwareVersionField = new TextField();
    private final TextField hardwareMacField = new TextField();
    private final TextField chipIdField = new TextField();
    private final TextField mqttTopicRootField = new TextField();
    private final TextField mqttUsernameField = new TextField();
    private final TextField mqttPasswordField = new TextField();
    private final TextField claimCodeField = new TextField();
    private final ComboBox<DevicePlatform> platformField = new ComboBox<>();
    private final ComboBox<MqttDeviceProfile> profileField = new ComboBox<>();
    private final TextArea metadataField = new TextArea();
    private String lastSuggestedTopicRoot;
    private String lastSuggestedMqttUsername;

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

        setWidthFull();
        setPadding(true);
        setSpacing(true);

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setMaxWidth("1200px");
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
                productModelField,
                platformField,
                profileField,
                firmwareVersionField,
                hardwareMacField,
                chipIdField,
                claimCodeField,
                mqttTopicRootField,
                mqttUsernameField,
                mqttPasswordField,
                metadataField,
                actions
        );
        formLayout.setWidthFull();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("720px", 2)
        );
        formLayout.setColspan(metadataField, 2);
        formLayout.setColspan(actions, 2);
        return formLayout;
    }

    private void configureForm() {
        serialNumberField.setLabel(t("admin.provisioning.serial"));
        serialNumberField.setRequired(true);

        productModelField.setLabel(t("admin.provisioning.productModel"));
        productModelField.setRequired(true);

        firmwareVersionField.setLabel(t("admin.provisioning.firmwareVersion"));
        hardwareMacField.setLabel(t("admin.provisioning.hardwareMac"));
        chipIdField.setLabel(t("admin.provisioning.chipId"));
        mqttTopicRootField.setLabel(t("admin.provisioning.topicRoot"));
        mqttUsernameField.setLabel(t("admin.provisioning.mqttUsername"));
        mqttPasswordField.setLabel(t("admin.provisioning.mqttPassword"));
        claimCodeField.setLabel(t("admin.provisioning.claimCode"));
        claimCodeField.setHelperText(t("admin.provisioning.claimCodeHelper"));

        platformField.setLabel(t("admin.provisioning.platform"));
        platformField.setItems(DevicePlatform.values());
        platformField.setRequired(true);

        profileField.setLabel(t("admin.provisioning.profile"));
        profileField.setItems(MqttDeviceProfile.values());
        profileField.setValue(MqttDeviceProfile.GENERIC_RELAY);

        metadataField.setLabel(t("admin.provisioning.metadata"));
        metadataField.setMinHeight("120px");

        mqttTopicRootField.setHelperText(t("admin.provisioning.topicRootHelper"));
        mqttUsernameField.setHelperText(t("admin.provisioning.mqttUsernameHelper"));
        mqttPasswordField.setHelperText(t("admin.provisioning.mqttPasswordHelper"));

        serialNumberField.addValueChangeListener(event -> applySerialBasedDefaults(event.getValue()));
    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.addColumn(FactoryDeviceResponse::getSerialNumber).setHeader(t("admin.provisioning.serial")).setAutoWidth(true);
        grid.addColumn(FactoryDeviceResponse::getProductModel).setHeader(t("admin.provisioning.productModel")).setAutoWidth(true);
        grid.addColumn(item -> item.getPlatform() != null ? item.getPlatform().name() : "")
                .setHeader(t("admin.provisioning.platform")).setAutoWidth(true);
        grid.addColumn(item -> item.getMqttDeviceProfile() != null ? item.getMqttDeviceProfile().name() : "")
                .setHeader(t("admin.provisioning.profile")).setAutoWidth(true);
        grid.addColumn(FactoryDeviceResponse::getClaimCode).setHeader(t("admin.provisioning.claimCode")).setAutoWidth(true);
        grid.addColumn(item -> item.getStatus() != null ? item.getStatus().name() : "")
                .setHeader(t("admin.provisioning.status")).setAutoWidth(true);
        grid.addColumn(FactoryDeviceResponse::getMqttTopicRoot).setHeader(t("admin.provisioning.topicRoot")).setAutoWidth(true).setFlexGrow(1);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Europe/Helsinki"));
        grid.addColumn(item -> item.getLastSeenAt() != null ? formatter.format(item.getLastSeenAt()) : "")
                .setHeader(t("admin.provisioning.lastSeen")).setAutoWidth(true);
    }

    private void refreshGrid() {
        List<FactoryDeviceResponse> items = factoryProvisioningService.listFactoryDevices();
        grid.setItems(items);
    }

    private void createFactoryDevice() {
        try {
            CreateFactoryDeviceRequest request = new CreateFactoryDeviceRequest();
            request.setSerialNumber(serialNumberField.getValue());
            request.setProductModel(productModelField.getValue());
            request.setPlatform(platformField.getValue());
            request.setMqttDeviceProfile(profileField.getValue());
            request.setFirmwareVersion(firmwareVersionField.getValue());
            request.setHardwareMac(hardwareMacField.getValue());
            request.setChipId(chipIdField.getValue());
            request.setMqttTopicRoot(mqttTopicRootField.getValue());
            request.setMqttUsername(mqttUsernameField.getValue());
            request.setMqttPassword(mqttPasswordField.getValue());
            request.setClaimCode(claimCodeField.getValue());
            request.setMetadataJson(metadataField.getValue());

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

    private void clearForm() {
        serialNumberField.clear();
        productModelField.clear();
        firmwareVersionField.clear();
        hardwareMacField.clear();
        chipIdField.clear();
        mqttTopicRootField.clear();
        mqttUsernameField.clear();
        mqttPasswordField.clear();
        claimCodeField.clear();
        metadataField.clear();
        platformField.clear();
        profileField.setValue(MqttDeviceProfile.GENERIC_RELAY);
        lastSuggestedTopicRoot = null;
        lastSuggestedMqttUsername = null;
    }

    private void applySerialBasedDefaults(String serialNumber) {
        String suggestedTopicRoot = buildSuggestedTopicRoot(serialNumber);
        String suggestedMqttUsername = buildSuggestedMqttUsername(serialNumber);

        if (shouldApplySuggestion(mqttTopicRootField.getValue(), lastSuggestedTopicRoot)) {
            mqttTopicRootField.setValue(suggestedTopicRoot);
        }
        if (shouldApplySuggestion(mqttUsernameField.getValue(), lastSuggestedMqttUsername)) {
            mqttUsernameField.setValue(suggestedMqttUsername);
        }

        lastSuggestedTopicRoot = suggestedTopicRoot;
        lastSuggestedMqttUsername = suggestedMqttUsername;
    }

    private boolean shouldApplySuggestion(String currentValue, String previousSuggestion) {
        return currentValue == null || currentValue.isBlank() || currentValue.equals(previousSuggestion);
    }

    private String buildSuggestedTopicRoot(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return "";
        }
        return "factory/bootstrap/" + serialNumber.trim();
    }

    private String buildSuggestedMqttUsername(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return "";
        }
        String normalized = serialNumber.toLowerCase().replaceAll("[^a-z0-9]", "");
        return normalized.isBlank() ? "" : "factory-" + normalized;
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToHomeIfNotAdmin(event, authService);
    }
}
