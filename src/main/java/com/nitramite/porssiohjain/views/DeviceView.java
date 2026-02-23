package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
    protected final I18nService i18n;

    private final TextField nameField;
    private final ComboBox<String> timezoneCombo;
    private final Button saveButton;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DeviceResponse selectedDevice;

    @Autowired
    public DeviceView(
            DeviceService deviceService,
            AuthService authService,
            I18nService i18n
    ) {
        this.deviceService = deviceService;
        this.authService = authService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        nameField = new TextField(t("device.field.name"));
        timezoneCombo = new ComboBox<>(t("device.field.timezone"));
        saveButton = new Button(t("device.button.save"));

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        card.getStyle().set("border-radius", "12px");
        card.getStyle().set("padding", "32px");
        card.getStyle().set("background-color", "var(--lumo-base-color)");

        H2 title = new H2(t("device.title"));
        title.getStyle().set("margin-top", "0");

        deviceGrid.addColumn(DeviceResponse::getId).setHeader(t("device.grid.id")).setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getDeviceName).setHeader(t("device.grid.name")).setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getUuid).setHeader(t("device.grid.uuid")).setAutoWidth(true);
        deviceGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            Instant lastComm = control.getLastCommunication();
            return lastComm != null
                    ? ZonedDateTime.ofInstant(lastComm, zone).format(formatter)
                    : "-";
        }).setHeader(t("device.grid.lastCommunication")).setAutoWidth(true);
        deviceGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            return ZonedDateTime.ofInstant(control.getCreatedAt(), zone).format(formatter);
        }).setHeader(t("device.grid.created")).setAutoWidth(true);
        deviceGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            return ZonedDateTime.ofInstant(control.getUpdatedAt(), zone).format(formatter);
        }).setHeader(t("device.grid.updated")).setAutoWidth(true);

        deviceGrid.setWidthFull();
        deviceGrid.getStyle().set("max-height", "300px");
        deviceGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        deviceGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedDevice = event.getValue();
            if (selectedDevice != null) {
                nameField.setValue(selectedDevice.getDeviceName());
                timezoneCombo.setValue(selectedDevice.getTimezone());
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

        FormLayout formLayout = new FormLayout();
        formLayout.setWidthFull();
        formLayout.getStyle().set("margin-top", "20px");
        formLayout.add(nameField, timezoneCombo);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 3)
        );
        saveButton.getStyle().set("align-self", "start");

        card.add(title, deviceGrid, formLayout, saveButton);
        add(card);

        loadDevices();
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

            if (selectedDevice != null) {
                deviceService.updateDevice(selectedDevice.getId(), deviceName, timezone);
                Notification notification = Notification.show(t("device.notification.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                deviceService.createDevice(authAccountId, accountId, deviceName, timezone);
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
        nameField.clear();
        timezoneCombo.setValue(ZoneId.systemDefault().getId());
        saveButton.setText(t("device.button.add"));
        deviceGrid.deselectAll();
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
