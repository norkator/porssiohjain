package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.ControlSchedulerService;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.models.ControlDeviceResponse;
import com.nitramite.porssiohjain.services.models.ControlResponse;
import com.nitramite.porssiohjain.services.models.ControlTableResponse;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
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
    private final Grid<ControlTableResponse> controlTableGrid = new Grid<>(ControlTableResponse.class, false);

    private final ControlService controlService;
    private final DeviceService deviceService;
    private final ControlSchedulerService controlSchedulerService;

    @Autowired
    public ControlView(
            ControlService controlService,
            DeviceService deviceService,
            ControlSchedulerService controlSchedulerService
    ) {
        this.controlService = controlService;
        this.deviceService = deviceService;
        this.controlSchedulerService = controlSchedulerService;

        add(new H2("Device Controls"));

        // Configure controls grid
        controlsGrid.getStyle().set("max-height", "200px");
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
                .setWidth("90%")
                .set("margin-top", "1rem");

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
                // new Paragraph("ID: " + control.getId()),
                new Paragraph("Name: " + control.getName()),
                // new Paragraph("Max price: " + control.getMaxPriceSnt() + " snt"),
                // new Paragraph("Daily on minutes: " + control.getDailyOnMinutes()),
                // new Paragraph("Created: " + control.getCreatedAt()),
                // new Paragraph("Updated: " + control.getUpdatedAt()),
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

        Div hr = new Div();
        hr.getStyle()
                .set("width", "100%")
                .set("height", "1px")
                .set("background-color", "var(--lumo-contrast-20pct)")
                .set("margin", "1rem 0");
        detailCard.add(hr);

        VerticalLayout controlTableConfiguration = getControlTableConfiguration(control);
        detailCard.add(controlTableConfiguration);
        refreshControlTable(control.getId());
    }

    private void configureDeviceGrid(ControlResponse control) {
        deviceGrid.removeAllColumns();
        deviceGrid.getStyle().set("max-height", "250px");
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

    private HorizontalLayout createAddDeviceLayout(
            Long controlId
    ) {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>("Select Device");
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);

        // Load available devices
        try {
            List<DeviceResponse> devices = deviceService.getAllDevices(controlId);
            deviceSelect.setItems(devices);
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

    private VerticalLayout getControlTableConfiguration(ControlResponse control) {
        H3 title = new H3("Control table");

        // configure grid
        controlTableGrid.removeAllColumns();
        controlTableGrid.addColumn(ControlTableResponse::getStartTime).setHeader("Start Time");
        controlTableGrid.addColumn(ControlTableResponse::getEndTime).setHeader("End Time");
        controlTableGrid.addColumn(ControlTableResponse::getPriceSnt).setHeader("Price (snt/kWh)");
        controlTableGrid.addColumn(ControlTableResponse::getStatus).setHeader("Status");

        controlTableGrid.setAllRowsVisible(true);


        Button recalcButton = new Button("Recalculate", click -> {
            try {
                controlSchedulerService.generateForControl(control.getId());
                Notification.show("Recalculated control successfully");
                refreshControlTable(control.getId());
            } catch (Exception e) {
                Notification.show("Failed to recalculate: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });

        recalcButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Layout: title + button horizontally
        HorizontalLayout headerLayout = new HorizontalLayout(title, recalcButton);
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        headerLayout.setWidthFull();
        headerLayout.expand(title);

        // Combine header + grid vertically
        VerticalLayout layout = new VerticalLayout(headerLayout, controlTableGrid);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setWidthFull();

        return layout;
    }

    private void refreshControlTable(Long controlId) {
        List<ControlTableResponse> entries = controlSchedulerService.findByControlId(controlId);
        controlTableGrid.setItems(entries);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
        }
    }
}