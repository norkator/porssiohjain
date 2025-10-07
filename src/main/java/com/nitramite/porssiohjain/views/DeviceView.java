package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Route("device")
@PermitAll
public class DeviceView extends VerticalLayout {

    private final Grid<DeviceResponse> deviceGrid = new Grid<>(DeviceResponse.class, false);
    private final DeviceService deviceService;
    private final AuthService authService;

    @Autowired
    public DeviceView(
            DeviceService deviceService,
            AuthService authService
    ) {
        this.deviceService = deviceService;
        this.authService = authService;

        add(new H2("My devices"));

        // Configure device grid
        deviceGrid.addColumn(DeviceResponse::getId).setHeader("ID").setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getDeviceName).setHeader("Device Name").setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getUuid).setHeader("UUID").setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getLastCommunication).setHeader("Last Communication").setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getCreatedAt).setHeader("Created At").setAutoWidth(true);
        deviceGrid.addColumn(DeviceResponse::getUpdatedAt).setHeader("Updated At").setAutoWidth(true);

        deviceGrid.setWidthFull();
        deviceGrid.getStyle().set("max-height", "300px");

        add(deviceGrid);

        // Load initial devices
        loadDevices();

        // Add new device form
        HorizontalLayout addDeviceLayout = createAddDeviceForm();
        add(addDeviceLayout);
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

    private HorizontalLayout createAddDeviceForm() {
        TextField nameField = new TextField("Device Name");
        Button addButton = new Button("Add Device", e -> {
            String deviceName = nameField.getValue();
            if (deviceName == null || deviceName.isBlank()) {
                Notification.show("Device name cannot be empty");
                return;
            }

            try {
                String token = (String) VaadinSession.getCurrent().getAttribute("token");
                AccountEntity currentAccount = authService.authenticate(token);
                Long accountId = currentAccount.getId();

                deviceService.createDevice(accountId, deviceName);
                Notification.show("Device created successfully!");
                nameField.clear();
                loadDevices(); // reload table
            } catch (Exception ex) {
                Notification.show("Failed to create device: " + ex.getMessage());
            }
        });

        return new HorizontalLayout(nameField, addButton);
    }
}