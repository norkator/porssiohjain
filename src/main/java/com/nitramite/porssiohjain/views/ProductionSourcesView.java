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
import com.nitramite.porssiohjain.entity.ProductionApiType;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.ProductionSourceService;
import com.nitramite.porssiohjain.services.models.ProductionSourceResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@PageTitle("Pörssiohjain - Production Sources")
@Route("production-sources")
@PermitAll
public class ProductionSourcesView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<ProductionSourceResponse> sourcesGrid = new Grid<>(ProductionSourceResponse.class, false);
    private final ProductionSourceService productionSourceService;
    private final I18nService i18n;

    private Long accountId;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TextField nameField;
    private final ComboBox<ProductionApiType> apiTypeField;
    private final TextField appIdField;
    private final PasswordField appSecretField;
    private final EmailField emailField;
    private final PasswordField passwordField;
    private final TextField stationIdField;
    private final Checkbox enabledToggle;
    private final Button createButton;

    @Autowired
    public ProductionSourcesView(
            ProductionSourceService productionSourceService,
            AuthService authService,
            I18nService i18n
    ) {
        this.productionSourceService = productionSourceService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        nameField = new TextField(t("productionsources.field.name"));
        apiTypeField = new ComboBox<>(t("productionsources.field.apiType"));
        appIdField = new TextField(t("productionsources.field.appId"));
        appSecretField = new PasswordField(t("productionsources.field.appSecret"));
        emailField = new EmailField(t("productionsources.field.email"));
        passwordField = new PasswordField(t("productionsources.field.password"));
        stationIdField = new TextField(t("productionsources.field.stationId"));
        enabledToggle = new Checkbox(t("productionsources.field.enabled"));
        createButton = new Button(t("productionsources.button.create"));

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setMaxWidth("1400px");
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 title = new H2(t("productionsources.title"));
        title.getStyle().set("margin-top", "0");

        configureGrid();
        configureForm();

        card.add(title, sourcesGrid, createFormLayout());
        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification notification = Notification.show(t("productionsources.notification.sessionExpired"));
            notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
            UI.getCurrent().navigate(LoginView.class);
            return;
        }

        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();

        loadSources();
    }

    private void configureForm() {
        nameField.setWidthFull();

        apiTypeField.setItems(ProductionApiType.values());
        apiTypeField.setWidthFull();

        appIdField.setWidthFull();
        appSecretField.setWidthFull();
        emailField.setWidthFull();
        passwordField.setWidthFull();
        stationIdField.setWidthFull();

        enabledToggle.setValue(true);

        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> createNewSource());
    }

    private Component createFormLayout() {
        VerticalLayout container = new VerticalLayout();
        container.setPadding(false);
        container.setSpacing(false);
        container.getStyle()
                .set("margin-top", "20px")
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        FormLayout form = new FormLayout();
        form.add(
                nameField,
                apiTypeField,
                appIdField,
                appSecretField,
                emailField,
                passwordField,
                stationIdField,
                enabledToggle
        );

        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("900px", 3)
        );

        container.add(form, createButton);
        return container;
    }

    private void configureGrid() {
        sourcesGrid.addColumn(ProductionSourceResponse::getId)
                .setHeader(t("productionsources.grid.id"))
                .setAutoWidth(true);

        sourcesGrid.addColumn(ProductionSourceResponse::getName)
                .setHeader(t("productionsources.grid.name"))
                .setAutoWidth(true);

        sourcesGrid.addColumn(ProductionSourceResponse::getApiType)
                .setHeader(t("productionsources.grid.apiType"))
                .setAutoWidth(true);

        sourcesGrid.addColumn(ProductionSourceResponse::getCurrentKw)
                .setHeader(t("productionsources.grid.currentKw"))
                .setAutoWidth(true);

        sourcesGrid.addColumn(ProductionSourceResponse::getPeakKw)
                .setHeader(t("productionsources.grid.peakKw"))
                .setAutoWidth(true);

        sourcesGrid.addComponentColumn(productionSourceResponse -> {
            boolean enabled = productionSourceResponse.isEnabled();
            String text = enabled ? t("common.yes") : t("common.no");
            Span badge = new Span(text);
            badge.getElement().getThemeList().add("badge");
            badge.getElement().getThemeList().add(enabled ? "success" : "error");
            return badge;
        }).setHeader(t("productionsources.grid.enabled")).setAutoWidth(true);

        sourcesGrid.addColumn(src -> {
            ZoneId zone = ZoneId.systemDefault();
            return ZonedDateTime.ofInstant(src.getCreatedAt(), zone).format(formatter);
        }).setHeader(t("productionsources.grid.created")).setAutoWidth(true);

        sourcesGrid.addColumn(src -> {
            ZoneId zone = ZoneId.systemDefault();
            return ZonedDateTime.ofInstant(src.getUpdatedAt(), zone).format(formatter);
        }).setHeader(t("productionsources.grid.updated")).setAutoWidth(true);

        sourcesGrid.setWidthFull();
        sourcesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        sourcesGrid.getStyle().set("max-height", "300px");

        sourcesGrid.asSingleSelect().addValueChangeListener(event -> {
            ProductionSourceResponse selected = event.getValue();
            if (selected != null) {
                getUI().ifPresent(ui ->
                        ui.navigate(ProductionSourceView.class,
                                new RouteParameters("sourceId", selected.getId().toString()))
                );
            }
        });
    }

    private void createNewSource() {
        try {
            if (nameField.isEmpty() || apiTypeField.isEmpty()) {
                Notification notification = Notification.show(t("productionsources.notification.requiredMissing"));
                notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            productionSourceService.createSource(
                    accountId,
                    nameField.getValue(),
                    apiTypeField.getValue(),
                    appIdField.getValue(),
                    appSecretField.getValue(),
                    emailField.getValue(),
                    passwordField.getValue(),
                    stationIdField.getValue(),
                    enabledToggle.getValue()
            );

            Notification notification = Notification.show(t("productionsources.notification.created"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            clearForm();
            loadSources();
        } catch (Exception e) {
            Notification notification = Notification.show(t("productionsources.notification.failed", e.getMessage()));
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void clearForm() {
        nameField.clear();
        apiTypeField.clear();
        appIdField.clear();
        appSecretField.clear();
        emailField.clear();
        passwordField.clear();
        stationIdField.clear();
        enabledToggle.setValue(true);
    }

    private void loadSources() {
        try {
            sourcesGrid.setItems(productionSourceService.getAllSources(accountId));
        } catch (Exception e) {
            Notification notification = Notification.show(t("productionsources.notification.loadFailed", e.getMessage()));
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
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
