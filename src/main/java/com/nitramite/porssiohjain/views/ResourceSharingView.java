package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ResourceType;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@PageTitle("Pörssiohjain - Resource sharing")
@Route("resource-sharing")
@PermitAll
public class ResourceSharingView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<ResourceSharingItem> resourcesGrid = new Grid<>(ResourceSharingItem.class, false);
    private final I18nService i18n;

    private Long accountId;

    private final DeviceService deviceService;
    private final ControlService controlService;
    private final ProductionSourceService productionSourceService;
    private final PowerLimitService powerLimitService;

    @Autowired
    public ResourceSharingView(
            AuthService authService,
            I18nService i18n,
            DeviceService deviceService,
            ControlService controlService,
            ProductionSourceService productionSourceService,
            PowerLimitService powerLimitService
    ) {
        this.i18n = i18n;
        this.deviceService = deviceService;
        this.controlService = controlService;
        this.productionSourceService = productionSourceService;
        this.powerLimitService = powerLimitService;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)")
                .set("border-radius", "12px")
                .set("padding", "32px")
                .set("background-color", "var(--lumo-base-color)");

        H2 title = new H2(t("resourceSharing.title"));

        configureGrid();

        card.add(title, resourcesGrid, createFormLayout());
        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();

        loadAvailableResources();
        loadSites();
    }

    private Component createFormLayout() {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        VerticalLayout container = new VerticalLayout(form);
        container.getStyle().set("margin-top", "20px");

        return container;
    }

    private void configureGrid() {
        resourcesGrid.addColumn(ResourceSharingItem::getId).setHeader("ID").setAutoWidth(true);
        resourcesGrid.addColumn(ResourceSharingItem::getName).setHeader(t("resourceSharing.grid.name")).setAutoWidth(true);
        resourcesGrid.addColumn(r -> t("resourceSharing.type." + r.getResourceType()))
                .setHeader(t("resourceSharing.grid.type")).setAutoWidth(true);
        resourcesGrid.addComponentColumn(resource -> {
            boolean isShared = resource.isShared();
            String text = isShared ? t("common.yes") : t("common.no");
            Span badge = new Span(text);
            badge.getElement().getThemeList().add("badge");
            badge.getElement().getThemeList().add(isShared ? "success" : "error");
            return badge;
        }).setHeader(t("resourceSharing.grid.shared")).setAutoWidth(true);

        resourcesGrid.setWidthFull();
        resourcesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        resourcesGrid.asSingleSelect().addValueChangeListener(event -> {
            ResourceSharingItem selected = event.getValue();
            if (selected != null) {
                // ...
            }
        });
    }

    private void shareResource() {
        Notification notification = Notification.show(t("sites.notification.created"));
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        clearForm();
        loadSites();
    }

    private void removeResourceShare() {
        Notification notification = Notification.show(t("sites.notification.updated"));
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        clearForm();
        loadSites();
    }

    private void clearForm() {
        resourcesGrid.deselectAll();
    }

    private void loadAvailableResources() {
        List<DeviceResponse> devices = deviceService.getAllDevices(accountId);
        List<ControlResponse> controls = controlService.getAllControls(accountId);
        List<ProductionSourceResponse> productionSources = productionSourceService.getAllSources(accountId);
        List<PowerLimitResponse> powerLimits = powerLimitService.getAllLimits(accountId);

        List<ResourceSharingItem> resourceSharingItems = new ArrayList<>();
        long listIndex = 0;

        for (DeviceResponse device : devices) {
            resourceSharingItems.add(
                    ResourceSharingItem.builder()
                            .id(listIndex)
                            .resourceType(ResourceType.DEVICE)
                            .resourceId(device.getId())
                            .name(device.getDeviceName())
                            .build()
            );
            listIndex++;
        }

        for (ControlResponse control : controls) {
            resourceSharingItems.add(
                    ResourceSharingItem.builder()
                            .id(listIndex)
                            .resourceType(ResourceType.CONTROL)
                            .resourceId(control.getId())
                            .name(control.getName())
                            .build()
            );
            listIndex++;
        }

        for (ProductionSourceResponse ps : productionSources) {
            resourceSharingItems.add(
                    ResourceSharingItem.builder()
                            .id(listIndex)
                            .resourceType(ResourceType.PRODUCTION_SOURCE)
                            .resourceId(ps.getId())
                            .name(ps.getName())
                            .build()
            );
            listIndex++;
        }

        for (PowerLimitResponse pl : powerLimits) {
            resourceSharingItems.add(
                    ResourceSharingItem.builder()
                            .id(listIndex)
                            .resourceType(ResourceType.PRODUCTION_SOURCE)
                            .resourceId(pl.getId())
                            .name(pl.getName())
                            .build()
            );
            listIndex++;
        }

        resourcesGrid.setItems(resourceSharingItems);
    }

    private void loadSites() {
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (VaadinSession.getCurrent().getAttribute("token") == null) {
            event.forwardTo(LoginView.class);
        }
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
