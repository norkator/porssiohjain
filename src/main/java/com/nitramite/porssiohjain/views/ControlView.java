package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.models.ControlResponse;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Route("controls")
@PermitAll
public class ControlView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<ControlResponse> controlsGrid = new Grid<>(ControlResponse.class, false);
    private final ControlService controlService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    @Autowired
    public ControlView(ControlService controlService) {
        this.controlService = controlService;

        add(new H2("Device Controls"));
        controlsGrid.addColumn(ControlResponse::getId).setHeader("ID").setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getName).setHeader("Name").setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getMaxPriceSnt).setHeader("Max Price (snt)").setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getDailyOnMinutes).setHeader("Daily On Minutes").setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getTimezone).setHeader("Timezone").setAutoWidth(true);
        controlsGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            return ZonedDateTime.ofInstant(control.getCreatedAt(), zone).format(formatter);
        }).setHeader("Created").setAutoWidth(true);
        controlsGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            return ZonedDateTime.ofInstant(control.getUpdatedAt(), zone).format(formatter);
        }).setHeader("Updated").setAutoWidth(true);

        controlsGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        controlsGrid.asSingleSelect().addValueChangeListener(event -> {
            ControlResponse selected = event.getValue();
            if (selected != null) {
                getUI().ifPresent(ui ->
                        ui.navigate(ControlTableView.class,
                                new RouteParameters("controlId", selected.getId().toString()))
                );
            }
        });

        loadControls();
        add(controlsGrid);
    }

    private void loadControls() {
        try {
            controlsGrid.setItems(controlService.getAllControls());
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
