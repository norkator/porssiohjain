package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.SiteType;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.SiteService;
import com.nitramite.porssiohjain.services.models.SiteResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

@PageTitle("Pörssiohjain - Sites")
@Route("sites")
@PermitAll
public class SitesView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<SiteResponse> sitesGrid = new Grid<>(SiteResponse.class, false);
    private final SiteService siteService;
    private final I18nService i18n;

    private Long accountId;
    private Long editingSiteId = null;

    private final TextField nameField;
    private final ComboBox<SiteType> typeField;
    private final Checkbox enabledToggle;
    private final Button saveButton;

    @Autowired
    public SitesView(SiteService siteService, AuthService authService, I18nService i18n) {
        this.siteService = siteService;
        this.i18n = i18n;

        nameField = new TextField(t("sites.field.name"));
        typeField = new ComboBox<>(t("sites.field.type"));
        enabledToggle = new Checkbox(t("sites.field.enabled"));
        saveButton = new Button(t("sites.button.create"));

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

        H2 title = new H2(t("sites.title"));

        configureGrid();
        configureForm();

        card.add(title, sitesGrid, createFormLayout());
        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();

        loadSites();
    }

    private void configureForm() {
        nameField.setWidthFull();

        typeField.setItems(SiteType.values());
        typeField.setItemLabelGenerator(type -> t("siteType." + type.name()));
        typeField.setWidthFull();

        enabledToggle.setValue(true);

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            if (editingSiteId == null) {
                createNewSite();
            } else {
                updateSite();
            }
        });
    }

    private Component createFormLayout() {
        FormLayout form = new FormLayout(nameField, typeField, enabledToggle);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        VerticalLayout container = new VerticalLayout(form, saveButton);
        container.getStyle().set("margin-top", "20px");

        return container;
    }

    private void configureGrid() {
        sitesGrid.addColumn(SiteResponse::getId).setHeader("ID").setAutoWidth(true);
        sitesGrid.addColumn(SiteResponse::getName).setHeader(t("sites.grid.name")).setAutoWidth(true);
        sitesGrid.addColumn(site -> t("siteType." + site.getType().name()))
                .setHeader(t("sites.grid.type")).setAutoWidth(true);
        sitesGrid.addColumn(SiteResponse::getEnabled).setHeader(t("sites.grid.enabled")).setAutoWidth(true);

        sitesGrid.setWidthFull();
        sitesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        sitesGrid.asSingleSelect().addValueChangeListener(event -> {
            SiteResponse selected = event.getValue();
            if (selected != null) {
                editingSiteId = selected.getId();
                nameField.setValue(selected.getName());
                typeField.setValue(selected.getType());
                enabledToggle.setValue(selected.getEnabled());
                saveButton.setText(t("sites.button.update"));
            }
        });
    }

    private void createNewSite() {
        siteService.createSite(accountId, nameField.getValue(), typeField.getValue(), enabledToggle.getValue());
        Notification.show(t("sites.notification.created"));
        clearForm();
        loadSites();
    }

    private void updateSite() {
        siteService.updateSite(editingSiteId, nameField.getValue(), typeField.getValue(), enabledToggle.getValue());
        Notification.show(t("sites.notification.updated"));
        clearForm();
        loadSites();
    }

    private void clearForm() {
        editingSiteId = null;
        nameField.clear();
        typeField.clear();
        enabledToggle.setValue(true);
        saveButton.setText(t("sites.button.create"));
        sitesGrid.deselectAll();
    }

    private void loadSites() {
        sitesGrid.setItems(siteService.getAllSites(accountId));
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
