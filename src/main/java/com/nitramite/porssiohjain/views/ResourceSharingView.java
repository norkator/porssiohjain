package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.ResourceSharingItem;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

@PageTitle("Pörssiohjain - Resource sharing")
@Route("resource-sharing")
@PermitAll
public class ResourceSharingView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<ResourceSharingItem> resourcesGrid = new Grid<>(ResourceSharingItem.class, false);
    private final I18nService i18n;

    private Long accountId;
    
    @Autowired
    public ResourceSharingView(
            AuthService authService, 
            I18nService i18n
    ) {
        this.i18n = i18n;
        
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

    private void loadSites() {
        // resourcesGrid.setItems(siteService.getAllSites(accountId));
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
