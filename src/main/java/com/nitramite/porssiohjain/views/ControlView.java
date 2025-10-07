package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.models.ControlDeviceResponse;
import com.nitramite.porssiohjain.services.models.ControlResponse;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
    private final ControlService controlService;

    @Autowired
    public ControlView(ControlService controlService) {
        this.controlService = controlService;

        add(new H2("Device Controls"));

        // Configure grid
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

        // Load data directly from service
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
        detailCard.add(
                new H2("Control details"),
                new Paragraph("ID: " + control.getId()),
                new Paragraph("Name: " + control.getName()),
                new Paragraph("Max price: " + control.getMaxPriceSnt() + " snt"),
                new Paragraph("Daily on minutes: " + control.getDailyOnMinutes()),
                new Paragraph("Created: " + control.getCreatedAt()),
                new Paragraph("Updated: " + control.getUpdatedAt())
        );

        loadControlDevices(control.getId());
    }

    private void loadControlDevices(
            Long controlId
    ) {
        try {
            List<ControlDeviceResponse> controlDevices = controlService.getControlDevices(controlId);

        } catch (Exception e) {
            Notification.show("Failed to load controls: " + e.getMessage());
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
        }
    }
}