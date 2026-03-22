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
import com.nitramite.porssiohjain.entity.ResourceSharingEntity;
import com.nitramite.porssiohjain.entity.enums.ResourceType;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PageTitle("Pörssiohjain - Resource sharing")
@Route("resource-sharing")
@PermitAll
public class ResourceSharingView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<ResourceSharingItem> resourcesGrid = new Grid<>(ResourceSharingItem.class, false);
    private final I18nService i18n;

    private final Long accountId;

    private final AccountService accountService;
    private final DeviceService deviceService;
    private final ControlService controlService;
    private final ProductionSourceService productionSourceService;
    private final PowerLimitService powerLimitService;
    private final ResourceSharingService resourceSharingService;

    private VerticalLayout formLayout;

    @Autowired
    public ResourceSharingView(
            AccountService accountService,
            AuthService authService,
            I18nService i18n,
            DeviceService deviceService,
            ControlService controlService,
            ProductionSourceService productionSourceService,
            PowerLimitService powerLimitService,
            ResourceSharingService resourceSharingService
    ) {
        this.i18n = i18n;
        this.deviceService = deviceService;
        this.controlService = controlService;
        this.productionSourceService = productionSourceService;
        this.powerLimitService = powerLimitService;
        this.resourceSharingService = resourceSharingService;
        this.accountService = accountService;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 title = new H2(t("resourceSharing.title"));

        configureGrid();

        card.add(title, resourcesGrid, createFormLayout());
        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();

        loadAvailableResources();
    }

    private Component createFormLayout() {
        formLayout = new VerticalLayout();
        formLayout.setWidthFull();
        formLayout.getStyle().set("margin-top", "20px");
        formLayout.add(new Span("Select a resource to manage sharing."));
        return formLayout;
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
        resourcesGrid.setMaxHeight("200px");

        resourcesGrid.asSingleSelect().addValueChangeListener(event -> {
            ResourceSharingItem selected = event.getValue();
            if (selected != null) {
                showFormFor(selected);
            }
        });
    }

    private void showFormFor(
            ResourceSharingItem item
    ) {
        formLayout.removeAll();
        H4 title = new H4(t("resourceSharing.form.title", item.getName()));
        Grid<ResourceSharingEntity> sharesGrid = new Grid<>(ResourceSharingEntity.class, false);
        sharesGrid.addColumn(share ->
                accountService.getUuidById(share.getReceiverAccountId())
        ).setHeader(t("resourceSharing.form.accountUuid")).setAutoWidth(true);
        sharesGrid.addComponentColumn(share -> {
            Button remove = new Button(t("resourceSharing.form.remove"), e -> {
                resourceSharingService.delete(accountId, share.getId());
                showFormFor(item);
            });
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return remove;
        });
        sharesGrid.setWidthFull();
        sharesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        sharesGrid.setMaxHeight("200px");
        List<ResourceSharingEntity> shares =
                resourceSharingService.getSharesForResource(
                        accountId,
                        item.getResourceType(),
                        item.getResourceId()
                );
        sharesGrid.setItems(shares);
        TextField uuidField = new TextField(t("resourceSharing.form.addAccountUuid"));
        uuidField.setWidthFull();
        Button addButton = new Button(t("resourceSharing.form.add"), e -> {
            try {
                UUID uuid = UUID.fromString(uuidField.getValue());
                Long receiverId = accountService.getIdByUuid(uuid);
                resourceSharingService.share(
                        accountId,
                        receiverId,
                        item.getResourceType(),
                        item.getResourceId()
                );
                Notification notification = Notification.show(t("sites.notification.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                uuidField.clear();
                showFormFor(item);
            } catch (Exception ex) {
                uuidField.setInvalid(true);
            }
        });
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout addRow = new HorizontalLayout(uuidField, addButton);
        addRow.setWidthFull();
        addRow.setFlexGrow(1, uuidField);
        formLayout.add(title, sharesGrid, addRow);
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
                            .resourceType(ResourceType.POWER_LIMIT)
                            .resourceId(pl.getId())
                            .name(pl.getName())
                            .build()
            );
            listIndex++;
        }

        resourcesGrid.setItems(resourceSharingItems);
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
