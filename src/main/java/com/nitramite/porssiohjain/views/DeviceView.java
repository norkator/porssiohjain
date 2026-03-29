/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcDevicesService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcMappingResponse;
import com.nitramite.porssiohjain.services.toshiba.ToshibaLoginService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@PageTitle("Pörssiohjain - Devices")
@Route("device")
@PermitAll
public class DeviceView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<DeviceResponse> deviceGrid = new Grid<>(DeviceResponse.class, false);
    private final DeviceService deviceService;
    private final AuthService authService;
    private final ToshibaLoginService toshibaLoginService;
    private final ToshibaAcDevicesService toshibaAcDevicesService;
    protected final I18nService i18n;

    private final TextField nameField;
    private final ComboBox<String> timezoneCombo;
    private final ComboBox<DeviceType> deviceTypeCombo;
    private final Checkbox enabledField;

    private FormLayout heatPumpForm;
    private TextField hpNameField;
    private ComboBox<AcType> acTypeCombo;
    private TextField acUsernameField;
    private PasswordField acPasswordField;
    private final Button selectAcDeviceButton;

    private final Button saveButton;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DeviceResponse selectedDevice;
    private String pendingAcDeviceId;

    @Autowired
    public DeviceView(
            DeviceService deviceService,
            AuthService authService,
            I18nService i18n,
            ToshibaLoginService toshibaLoginService,
            ToshibaAcDevicesService toshibaAcDevicesService
    ) {
        this.deviceService = deviceService;
        this.authService = authService;
        this.i18n = i18n;
        this.toshibaLoginService = toshibaLoginService;
        this.toshibaAcDevicesService = toshibaAcDevicesService;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        initHeatPumpForm();

        nameField = new TextField(t("device.field.name"));
        timezoneCombo = new ComboBox<>(t("device.field.timezone"));
        deviceTypeCombo = new ComboBox<>(t("device.grid.type"));
        enabledField = new Checkbox(t("device.field.enabled"));
        enabledField.setValue(true);
        deviceTypeCombo.setItems(DeviceType.values());
        deviceTypeCombo.setItemLabelGenerator(type -> switch (type) {
            case STANDARD -> t("device.type.standard");
            case HEAT_PUMP -> t("device.type.heatPump");
        });
        deviceTypeCombo.setValue(DeviceType.STANDARD);
        deviceTypeCombo.setHelperText(t("device.type.helper"));
        // deviceTypeCombo.setWidth("250px");

        saveButton = new Button(t("device.button.save"));

        selectAcDeviceButton = new Button(t("device.hp.selectDeviceButton"));
        selectAcDeviceButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        selectAcDeviceButton.addClickListener(e -> openAcDeviceSelectionDialog());
        selectAcDeviceButton.setVisible(false);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 title = new H2(t("device.title"));
        title.getStyle().set("margin-top", "0");

        deviceGrid.addColumn(DeviceResponse::getId).setHeader(t("device.grid.id")).setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getDeviceName).setHeader(t("device.grid.name")).setAutoWidth(true);
        deviceGrid.addColumn(device -> {
            return switch (device.getDeviceType()) {
                case STANDARD -> t("device.type.standard");
                case HEAT_PUMP -> t("device.type.heatPump");
            };
        }).setHeader(t("device.grid.type")).setAutoWidth(true);
        deviceGrid.addComponentColumn(device -> {
            boolean enabled = device.getEnabled() != null && device.getEnabled();
            Span badge = new Span(enabled ? t("common.yes") : t("common.no"));
            badge.getElement().getThemeList().add("badge");
            badge.getElement().getThemeList().add(enabled ? "success" : "error");
            return badge;
        }).setHeader(t("device.grid.enabled")).setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getUuid).setHeader(t("device.grid.uuid")).setAutoWidth(true);
        deviceGrid.addComponentColumn(device -> {
            ZoneId zone = ZoneId.of(device.getTimezone());
            Instant lastComm = device.getLastCommunication();
            String lastCommText = lastComm != null
                    ? ZonedDateTime.ofInstant(lastComm, zone).format(formatter)
                    : "-";

            Span badge = new Span(lastCommText);
            badge.getElement().getThemeList().add("badge");
            badge.getElement().getThemeList().add(lastComm != null ? "success" : "error");
            return badge;
        }).setHeader(t("device.grid.lastCommunication")).setAutoWidth(true);
        deviceGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            return ZonedDateTime.ofInstant(control.getCreatedAt(), zone).format(formatter);
        }).setHeader(t("device.grid.created")).setAutoWidth(true);
        deviceGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            return ZonedDateTime.ofInstant(control.getUpdatedAt(), zone).format(formatter);
        }).setHeader(t("device.grid.updated")).setAutoWidth(true);
        deviceGrid.addComponentColumn(device -> {
            Span badge = new Span(device.getShared() ? t("common.shared") : t("common.mine"));
            badge.getElement().getThemeList().add("badge");
            badge.getElement().getThemeList().add(device.getShared() ? "warning" : "contrast");
            return badge;
        }).setHeader(t("common.origin")).setAutoWidth(true);
        deviceGrid.addComponentColumn(device -> {
            Span badge = new Span(device.getApiOnline() != null && device.getApiOnline() ? "Online" : "Offline");
            badge.getElement().getThemeList().add("badge");
            badge.getElement().getThemeList().add(device.getApiOnline() != null && device.getApiOnline() ? "success" : "error");
            return badge;
        }).setHeader(t("device.grid.apiOnline")).setAutoWidth(true);
        deviceGrid.addComponentColumn(device -> {
            Span badge = new Span(device.getMqttOnline() != null && device.getMqttOnline() ? "Online" : "Offline");
            badge.getElement().getThemeList().add("badge");
            badge.getElement().getThemeList().add(device.getMqttOnline() != null && device.getMqttOnline() ? "success" : "error");
            return badge;
        }).setHeader(t("device.grid.mqttOnline")).setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getMqttUsername)
                .setHeader(t("device.grid.mqttUsername"))
                .setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getMqttPassword)
                .setHeader(t("device.grid.mqttPassword"))
                .setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getAcDeviceId)
                .setHeader(t("device.grid.acDeviceId"))
                .setAutoWidth(true);

        deviceGrid.setWidthFull();
        deviceGrid.getStyle().set("max-height", "300px");
        deviceGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        deviceGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedDevice = event.getValue();
            if (selectedDevice != null) {
                nameField.setValue(selectedDevice.getDeviceName());
                timezoneCombo.setValue(selectedDevice.getTimezone());
                deviceTypeCombo.setValue(selectedDevice.getDeviceType());
                enabledField.setValue(selectedDevice.getEnabled() == null || selectedDevice.getEnabled());
                if (selectedDevice.getDeviceType() == DeviceType.HEAT_PUMP) {
                    hpNameField.setValue(selectedDevice.getHpName() != null ? selectedDevice.getHpName() : "");
                    acTypeCombo.setValue(selectedDevice.getAcType() != null ? selectedDevice.getAcType() : AcType.NONE);
                    acUsernameField.setValue(selectedDevice.getAcUsername() != null ? selectedDevice.getAcUsername() : "");
                    acPasswordField.setValue(selectedDevice.getAcPassword() != null ? selectedDevice.getAcPassword() : "");
                    pendingAcDeviceId = selectedDevice.getAcDeviceId();
                    updateSelectAcDeviceButton();
                } else {
                    hpNameField.clear();
                    acTypeCombo.setValue(AcType.NONE);
                    acUsernameField.clear();
                    acPasswordField.clear();
                    pendingAcDeviceId = null;
                    updateSelectAcDeviceButton();
                }
                saveButton.setText(t("device.button.update"));
            } else {
                clearForm();
            }
        });

        timezoneCombo.setItems(ZoneId.getAvailableZoneIds());
        timezoneCombo.setWidth("250px");
        timezoneCombo.setValue(ZoneId.systemDefault().getId());

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> handleSave());
        saveButton.getStyle().set("align-self", "start");

        FormLayout formLayout = new FormLayout();
        formLayout.setWidthFull();
        formLayout.getStyle().set("margin-top", "20px");
        formLayout.add(nameField, timezoneCombo, deviceTypeCombo, enabledField);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 4)
        );

        heatPumpForm.setVisible(false);

        HorizontalLayout actions = new HorizontalLayout(saveButton, selectAcDeviceButton);
        actions.setAlignItems(Alignment.CENTER);

        card.add(title, deviceGrid, formLayout, heatPumpForm, actions);
        add(card);

        deviceTypeCombo.addValueChangeListener(event -> {
            DeviceType type = event.getValue();
            boolean isHeatPump = type == DeviceType.HEAT_PUMP;
            heatPumpForm.setVisible(isHeatPump);
            if (!isHeatPump) {
                pendingAcDeviceId = null;
            }
            updateSelectAcDeviceButton();
        });

        loadDevices();
    }

    private void openAcDeviceSelectionDialog() {
        try {
            if (deviceTypeCombo.getValue() != DeviceType.HEAT_PUMP) {
                return;
            }

            String token = (String) VaadinSession.getCurrent().getAttribute("token");
            AccountEntity currentAccount = authService.authenticate(token);
            DeviceAcDataEntity acData = buildAcDataForSelection(currentAccount.getId());

            if (!toshibaLoginService.login(acData).isSuccess()) {
                Notification.show(t("device.notification.toshibaLoginFailed")).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            List<ToshibaAcMappingResponse.AcDevice> acDevices = toshibaAcDevicesService.getAcDevices(acData);
            if (acDevices.isEmpty()) {
                Notification.show(t("device.notification.noAcDevicesFound")).addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            Dialog dialog = new Dialog();
            dialog.setHeaderTitle(t("device.hp.dialog.title"));

            Grid<ToshibaAcMappingResponse.AcDevice> acGrid = new Grid<>();
            acGrid.addColumn(ToshibaAcMappingResponse.AcDevice::getName).setHeader(t("device.hp.dialog.grid.name"));
            acGrid.addColumn(ToshibaAcMappingResponse.AcDevice::getId).setHeader(t("device.hp.dialog.grid.id"));
            acGrid.setItems(acDevices);
            acGrid.setWidth("500px");
            acGrid.setHeight("300px");

            Button selectButton = new Button(t("common.select"), e -> {
                var selected = acGrid.asSingleSelect().getValue();
                if (selected != null) {
                    pendingAcDeviceId = selected.getId();
                    if (selectedDevice != null) {
                        deviceService.updateAcDeviceId(currentAccount.getId(), selectedDevice.getId(), selected.getId());
                    }
                    updateSelectAcDeviceButton();
                    Notification.show(t("device.notification.acDeviceSelected")).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    dialog.close();
                    loadDevices();
                }
            });
            selectButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            Button cancelButton = new Button(t("common.cancel"), e -> dialog.close());

            dialog.getFooter().add(cancelButton, selectButton);
            dialog.add(new VerticalLayout(acGrid));
            dialog.open();

        } catch (Exception e) {
            Notification.show(t("device.notification.failed", e.getMessage())).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void initHeatPumpForm() {
        heatPumpForm = new FormLayout();
        heatPumpForm.setWidthFull();
        heatPumpForm.getStyle().set("margin-top", "10px");
        hpNameField = new TextField(t("device.hp.name"));
        acTypeCombo = new ComboBox<>(t("device.hp.acType"));
        acTypeCombo.setItems(AcType.values());
        acTypeCombo.setItemLabelGenerator(type -> switch (type) {
            case NONE -> t("acType.none");
            case TOSHIBA -> t("acType.toshiba");
            case MITSUBISHI -> t("acType.mitsubishi");
        });
        acTypeCombo.setValue(AcType.NONE);
        acUsernameField = new TextField(t("device.hp.username"));
        acPasswordField = new PasswordField(t("device.hp.password"));
        heatPumpForm.add(hpNameField, acTypeCombo, acUsernameField, acPasswordField);
        heatPumpForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );
    }

    private void handleSave() {
        try {
            String token = (String) VaadinSession.getCurrent().getAttribute("token");
            if (token == null) {
                Notification notification = Notification.show("Not logged in");
                notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            AccountEntity currentAccount = authService.authenticate(token);
            Long authAccountId = currentAccount.getId();
            Long accountId = currentAccount.getId();

            String deviceName = nameField.getValue();
            String timezone = timezoneCombo.getValue();
            DeviceType deviceType = deviceTypeCombo.getValue();
            boolean enabled = enabledField.getValue();

            if (deviceName == null || deviceName.isBlank()) {
                Notification notification = Notification.show(t("device.notification.nameEmpty"));
                notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            if (timezone == null || timezone.isBlank()) {
                Notification notification = Notification.show(t("device.notification.timezoneEmpty"));
                notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            String hpName = hpNameField.getValue();
            AcType acType = acTypeCombo.getValue();
            String acUsername = acUsernameField.getValue();
            String acPassword = acPasswordField.getValue();

            if (deviceType == DeviceType.HEAT_PUMP) {
                if (hpName == null || hpName.isBlank()) {
                    Notification.show(t("device.notification.hpNameEmpty")).addThemeVariants(NotificationVariant.LUMO_WARNING);
                    return;
                }
                if (acUsername == null || acUsername.isBlank()) {
                    Notification.show(t("device.notification.acUsernameEmpty")).addThemeVariants(NotificationVariant.LUMO_WARNING);
                    return;
                }
                if (acPassword == null || acPassword.isBlank()) {
                    Notification.show(t("device.notification.acPasswordEmpty")).addThemeVariants(NotificationVariant.LUMO_WARNING);
                    return;
                }
                if (pendingAcDeviceId == null || pendingAcDeviceId.isBlank()) {
                    Notification.show(t("device.notification.acDeviceRequired")).addThemeVariants(NotificationVariant.LUMO_WARNING);
                    return;
                }
            }

            if (selectedDevice != null) {
                deviceService.updateDevice(
                        accountId, selectedDevice.getId(), deviceName, timezone, deviceType, enabled,
                        hpName, acType, acUsername, acPassword, pendingAcDeviceId
                );
                Notification notification = Notification.show(t("device.notification.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                deviceService.createDevice(
                        authAccountId, accountId, deviceName, timezone, deviceType, enabled,
                        hpName, acType, acUsername, acPassword, pendingAcDeviceId
                );
                Notification notification = Notification.show(t("device.notification.created"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }

            clearForm();
            loadDevices();

        } catch (Exception e) {
            Notification notification = Notification.show(t("device.notification.failed", e.getMessage()));
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void clearForm() {
        selectedDevice = null;
        pendingAcDeviceId = null;
        nameField.clear();
        timezoneCombo.setValue(ZoneId.systemDefault().getId());
        deviceTypeCombo.setValue(DeviceType.STANDARD);
        enabledField.setValue(true);
        hpNameField.clear();
        acTypeCombo.setValue(AcType.NONE);
        acUsernameField.clear();
        acPasswordField.clear();
        updateSelectAcDeviceButton();
        saveButton.setText(t("device.button.add"));
        deviceGrid.deselectAll();
    }

    private void updateSelectAcDeviceButton() {
        boolean isHeatPump = deviceTypeCombo.getValue() == DeviceType.HEAT_PUMP;
        selectAcDeviceButton.setVisible(isHeatPump);
        if (isHeatPump) {
            selectAcDeviceButton.setText(pendingAcDeviceId != null && !pendingAcDeviceId.isBlank()
                    ? t("device.hp.changeDeviceButton")
                    : t("device.hp.selectDeviceButton"));
        }
    }

    private DeviceAcDataEntity buildAcDataForSelection(Long accountId) {
        String hpName = hpNameField.getValue();
        String acUsername = acUsernameField.getValue();
        String acPassword = acPasswordField.getValue();

        if (hpName == null || hpName.isBlank()) {
            throw new IllegalArgumentException(t("device.notification.hpNameEmpty"));
        }
        if (acUsername == null || acUsername.isBlank()) {
            throw new IllegalArgumentException(t("device.notification.acUsernameEmpty"));
        }
        if (acPassword == null || acPassword.isBlank()) {
            throw new IllegalArgumentException(t("device.notification.acPasswordEmpty"));
        }

        if (selectedDevice != null) {
            DeviceAcDataEntity acData = deviceService.getDeviceAcData(accountId, selectedDevice.getId());
            acData.setName(hpName);
            acData.setAcType(acTypeCombo.getValue());
            acData.setAcUsername(acUsername);
            acData.setAcPassword(acPassword);
            return acData;
        }

        return DeviceAcDataEntity.builder()
                .name(hpName)
                .acType(acTypeCombo.getValue())
                .acUsername(acUsername)
                .acPassword(acPassword)
                .build();
    }

    private void loadDevices() {
        try {
            String token = (String) VaadinSession.getCurrent().getAttribute("token");
            if (token == null) {
                Notification notification = Notification.show(t("device.notification.notLoggedIn"));
                notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            AccountEntity currentAccount = authService.authenticate(token);
            Long accountId = currentAccount.getId();

            List<DeviceResponse> devices = deviceService.getAllDevices(accountId);
            deviceGrid.setItems(devices);
        } catch (Exception e) {
            Notification notification = Notification.show(t("device.notification.loadFailed", e.getMessage()));
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
        }
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
