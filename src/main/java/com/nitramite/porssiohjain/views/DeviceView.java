package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route("device")
@PermitAll
public class DeviceView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<DeviceResponse> deviceGrid = new Grid<>(DeviceResponse.class, false);
    private final DeviceService deviceService;
    private final AuthService authService;
    protected final I18nService i18n;

    private final TextField nameField = new TextField("Device Name");
    private final ComboBox<String> timezoneCombo = new ComboBox<>("Timezone");
    private final Button saveButton = new Button("Save Device");

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

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        // card.setWidth("800px");
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        card.getStyle().set("border-radius", "12px");
        card.getStyle().set("padding", "32px");
        card.getStyle().set("background-color", "var(--lumo-base-color)");

        H2 title = new H2("My Devices");
        title.getStyle().set("margin-top", "0");

        deviceGrid.addColumn(DeviceResponse::getId).setHeader("ID").setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getDeviceName).setHeader("Device Name").setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getUuid).setHeader("UUID").setAutoWidth(true);
        deviceGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            Instant lastComm = control.getLastCommunication();
            return lastComm != null
                    ? ZonedDateTime.ofInstant(lastComm, zone).format(formatter)
                    : "-";
        }).setHeader("Last Communication").setAutoWidth(true);
        deviceGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            return ZonedDateTime.ofInstant(control.getCreatedAt(), zone).format(formatter);
        }).setHeader("Created").setAutoWidth(true);
        deviceGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            return ZonedDateTime.ofInstant(control.getUpdatedAt(), zone).format(formatter);
        }).setHeader("Updated").setAutoWidth(true);

        deviceGrid.setWidthFull();
        deviceGrid.getStyle().set("max-height", "300px");
        deviceGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        deviceGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedDevice = event.getValue();
            if (selectedDevice != null) {
                nameField.setValue(selectedDevice.getDeviceName());
                timezoneCombo.setValue(selectedDevice.getTimezone());
                saveButton.setText("Update Device");
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
                Notification.show("Not logged in");
                return;
            }

            AccountEntity currentAccount = authService.authenticate(token);
            Long authAccountId = currentAccount.getId();
            Long accountId = currentAccount.getId();

            String deviceName = nameField.getValue();
            String timezone = timezoneCombo.getValue();

            if (deviceName == null || deviceName.isBlank()) {
                Notification.show("Device name cannot be empty");
                return;
            }

            if (timezone == null || timezone.isBlank()) {
                Notification.show("Please select a timezone");
                return;
            }

            if (selectedDevice != null) {
                deviceService.updateDevice(selectedDevice.getId(), deviceName, timezone);
                Notification.show("Device updated successfully!");
            } else {
                deviceService.createDevice(authAccountId, accountId, deviceName, timezone);
                Notification.show("Device created successfully!");
            }

            clearForm();
            loadDevices();

        } catch (Exception e) {
            Notification.show("Failed to save device: " + e.getMessage());
        }
    }

    private void clearForm() {
        selectedDevice = null;
        nameField.clear();
        timezoneCombo.setValue(ZoneId.systemDefault().getId());
        saveButton.setText("Add Device");
        deviceGrid.deselectAll();
    }

    private void loadDevices() {
        try {
            String token = (String) VaadinSession.getCurrent().getAttribute("token");
            if (token == null) {
                Notification.show("Not logged in");
                return;
            }

            AccountEntity currentAccount = authService.authenticate(token);
            Long accountId = currentAccount.getId();

            List<DeviceResponse> devices = deviceService.getAllDevices(accountId);
            deviceGrid.setItems(devices);
        } catch (Exception e) {
            Notification.show("Failed to load devices: " + e.getMessage());
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
