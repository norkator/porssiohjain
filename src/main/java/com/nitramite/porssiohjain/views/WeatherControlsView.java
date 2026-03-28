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
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.SiteService;
import com.nitramite.porssiohjain.services.WeatherControlService;
import com.nitramite.porssiohjain.services.models.SiteResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@PageTitle("Pörssiohjain - Weather Controls")
@Route("weather-controls")
@PermitAll
public class WeatherControlsView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<WeatherControlResponse> weatherControlsGrid = new Grid<>(WeatherControlResponse.class, false);
    private final WeatherControlService weatherControlService;
    private final SiteService siteService;
    protected final I18nService i18n;
    private Long accountId;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TextField nameField;
    private final ComboBox<SiteResponse> siteField;
    private final Button createButton;

    @Autowired
    public WeatherControlsView(
            WeatherControlService weatherControlService,
            SiteService siteService,
            AuthService authService,
            I18nService i18n
    ) {
        this.weatherControlService = weatherControlService;
        this.siteService = siteService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        nameField = new TextField(t("weatherControl.field.name"));
        siteField = new ComboBox<>(t("weatherControl.field.site"));
        createButton = new Button(t("weatherControl.button.create"));

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 title = new H2(t("weatherControl.title"));
        title.getStyle().set("margin-top", "0");

        configureGrid();
        configureForm();

        card.add(title, weatherControlsGrid, createFormLayout());
        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification notification = Notification.show(t("weatherControl.notification.sessionExpired"));
            notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
            UI.getCurrent().navigate(LoginView.class);
            return;
        }

        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();

        siteField.setItems(siteService.getAllSites(accountId));
        loadWeatherControls();
    }

    private void configureForm() {
        nameField.setPlaceholder(t("weatherControl.field.name.placeholder"));
        nameField.setWidthFull();

        siteField.setItemLabelGenerator(SiteResponse::getName);
        siteField.setWidthFull();

        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> createNewWeatherControl());
    }

    private Component createFormLayout() {
        VerticalLayout formContainer = new VerticalLayout();
        formContainer.setPadding(false);
        formContainer.setSpacing(false);
        formContainer.getStyle()
                .set("margin-top", "20px")
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        FormLayout formLayout = new FormLayout();
        formLayout.add(nameField, siteField);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        createButton.getStyle().set("margin-top", "16px");
        formContainer.add(formLayout, createButton);
        return formContainer;
    }

    private void configureGrid() {
        weatherControlsGrid.addColumn(WeatherControlResponse::getId).setHeader("ID").setAutoWidth(true);
        weatherControlsGrid.addColumn(WeatherControlResponse::getName).setHeader(t("weatherControl.grid.name")).setAutoWidth(true);
        weatherControlsGrid.addColumn(WeatherControlResponse::getSiteName).setHeader(t("weatherControl.grid.site")).setAutoWidth(true);
        weatherControlsGrid.addColumn(control -> ZonedDateTime.ofInstant(control.getCreatedAt(), ZoneId.systemDefault()).format(formatter))
                .setHeader(t("weatherControl.grid.created")).setAutoWidth(true);
        weatherControlsGrid.addColumn(control -> ZonedDateTime.ofInstant(control.getUpdatedAt(), ZoneId.systemDefault()).format(formatter))
                .setHeader(t("weatherControl.grid.updated")).setAutoWidth(true);

        weatherControlsGrid.setWidthFull();
        weatherControlsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        weatherControlsGrid.getStyle().set("max-height", "250px");
        weatherControlsGrid.setSelectionMode(Grid.SelectionMode.SINGLE);

        weatherControlsGrid.asSingleSelect().addValueChangeListener(event -> {
            WeatherControlResponse selected = event.getValue();
            if (selected != null) {
                getUI().ifPresent(ui ->
                        ui.navigate(WeatherControlView.class,
                                new RouteParameters("weatherControlId", selected.getId().toString()))
                );
            }
        });
    }

    private void createNewWeatherControl() {
        try {
            String name = nameField.getValue();
            SiteResponse site = siteField.getValue();

            if (name == null || name.isBlank()) {
                Notification notification = Notification.show(t("weatherControl.notification.nameEmpty"));
                notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            if (site == null) {
                Notification notification = Notification.show(t("weatherControl.notification.siteEmpty"));
                notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            weatherControlService.createWeatherControl(accountId, name, site.getId());
            Notification notification = Notification.show(t("weatherControl.notification.created"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            nameField.clear();
            siteField.clear();
            loadWeatherControls();
        } catch (Exception ex) {
            Notification notification = Notification.show(t("weatherControl.notification.failed", ex.getMessage()));
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void loadWeatherControls() {
        weatherControlsGrid.setItems(weatherControlService.getAllWeatherControls(accountId));
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
