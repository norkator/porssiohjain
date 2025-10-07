package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.models.ControlDeviceResponse;
import com.nitramite.porssiohjain.services.models.ControlResponse;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Route("controls")
@PermitAll
public class ControlView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<ControlResponse> controlsGrid = new Grid<>(ControlResponse.class, false);
    private final Div detailCard = new Div();
    private final Grid<ControlDeviceResponse> deviceGrid = new Grid<>(ControlDeviceResponse.class, false);

    private final ControlService controlService;

    @Autowired
    public ControlView(ControlService controlService) {
        this.controlService = controlService;

        add(new H2("Device Controls"));

        // Configure controls grid
        controlsGrid.addColumn(ControlResponse::getId).setHeader("ID").setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getName).setHeader("Name").setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getMaxPriceSnt).setHeader("Max Price (snt)").setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getDailyOnMinutes).setHeader("Daily On Minutes").setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getCreatedAt).setHeader("Created").setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getUpdatedAt).setHeader("Updated").setAutoWidth(true);
        controlsGrid.setSelectionMode(Grid.SelectionMode.SINGLE);

        // Detail card styling
        detailCard.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "8px")
                .set("padding", "1rem")
                .set("margin-top", "1rem")
                .set("min-height", "100px");

        loadControls();

        controlsGrid.asSingleSelect().addValueChangeListener(event -> {
            ControlResponse selected = event.getValue();
            if (selected != null) {
                showDetails(selected);
            } else {
                detailCard.removeAll();
                detailCard.setVisible(false);
            }
        });

        add(controlsGrid, detailCard);
    }

    private void loadControls() {
        try {
            List<ControlResponse> controls = controlService.getAllControls();
            controlsGrid.setItems(controls);
        } catch (Exception e) {
            Notification.show("Failed to load controls: " + e.getMessage());
        }
    }

    private void showDetails(ControlResponse control) {
        detailCard.removeAll();
        detailCard.setVisible(true);

        // Title and summary
        detailCard.add(
                new H2("Control details"),
                new Paragraph("ID: " + control.getId()),
                new Paragraph("Name: " + control.getName()),
                new Paragraph("Max price: " + control.getMaxPriceSnt() + " snt"),
                new Paragraph("Daily on minutes: " + control.getDailyOnMinutes()),
                new Paragraph("Created: " + control.getCreatedAt()),
                new Paragraph("Updated: " + control.getUpdatedAt()),
                new H3("Devices linked to this control:")
        );

        // Configure device grid
        configureDeviceGrid(control);

        detailCard.add(deviceGrid);

        // Add new device section
        HorizontalLayout addDeviceLayout = createAddDeviceLayout(control.getId());
        detailCard.add(addDeviceLayout);

        // Load devices
        loadControlDevices(control.getId());
    }

    private void configureDeviceGrid(ControlResponse control) {
        deviceGrid.removeAllColumns();
        deviceGrid.addColumn(cd -> cd.getDevice().getDeviceName()).setHeader("Device Name").setAutoWidth(true);
        deviceGrid.addColumn(ControlDeviceResponse::getDeviceChannel).setHeader("Channel").setAutoWidth(true);
        deviceGrid.addColumn(cd -> cd.getDevice().getUuid()).setHeader("UUID").setAutoWidth(true);
        deviceGrid.addColumn(cd -> cd.getDevice().getLastCommunication()).setHeader("Last Communication").setAutoWidth(true);

        // Delete button
        deviceGrid.addComponentColumn(cd -> {
            Button deleteButton = new Button("Delete", e -> {
                try {
                    controlService.deleteControlDevice(cd.getId());
                    Notification.show("Device removed from control.");
                    loadControlDevices(control.getId());
                } catch (Exception ex) {
                    Notification.show("Failed to delete: " + ex.getMessage());
                }
            });
            deleteButton.getStyle().set("background-color", "var(--lumo-error-color)");
            deleteButton.getStyle().set("color", "white");
            return deleteButton;
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void loadControlDevices(Long controlId) {
        try {
            List<ControlDeviceResponse> controlDevices = controlService.getControlDevices(controlId);
            deviceGrid.setItems(controlDevices);
        } catch (Exception e) {
            Notification.show("Failed to load control devices: " + e.getMessage());
        }
    }

    private HorizontalLayout createAddDeviceLayout(Long controlId) {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>("Select Device");
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);

        // Load available devices
        try {
            // List<DeviceResponse> devices = controlService.getAllDevices(); // <-- assumes you have this method
            // deviceSelect.setItems(devices);
        } catch (Exception e) {
            Notification.show("Failed to load devices: " + e.getMessage());
        }

        NumberField channelField = new NumberField("Channel");
        channelField.setStep(1);
        channelField.setMin(0);

        Button addButton = new Button("Add Device", e -> {
            DeviceResponse selectedDevice = deviceSelect.getValue();
            if (selectedDevice == null) {
                Notification.show("Please select a device.");
                return;
            }
            if (channelField.getValue() == null) {
                Notification.show("Please enter a channel number.");
                return;
            }

            try {
                controlService.addDeviceToControl(controlId, selectedDevice.getId(), channelField.getValue().intValue());
                Notification.show("Device added to control.");
                loadControlDevices(controlId);
            } catch (Exception ex) {
                Notification.show("Failed to add device: " + ex.getMessage());
            }
        });

        return new HorizontalLayout(deviceSelect, channelField, addButton);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
        }
    }
}