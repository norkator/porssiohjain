/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.enums.SiteType;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.SiteService;
import com.nitramite.porssiohjain.services.models.SiteResponse;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastPointResponse;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@PageTitle("Pörssiohjain - Sites")
@Route("sites")
@PermitAll
public class SitesView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<SiteResponse> sitesGrid = new Grid<>(SiteResponse.class, false);
    private final SiteService siteService;
    private final AuthService authService;
    private final I18nService i18n;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long accountId;
    private Long editingSiteId = null;

    private final TextField nameField;
    private final ComboBox<String> weatherPlaceField;
    private final ComboBox<String> timezoneField;
    private final ComboBox<SiteType> typeField;
    private final Checkbox enabledToggle;
    private final Button saveButton;
    private final VerticalLayout weatherInfoSection;
    private final TextField weatherTimestampField;
    private final TextField temperatureField;
    private final TextField windSpeedField;
    private final TextField humidityField;

    @Autowired
    public SitesView(SiteService siteService, AuthService authService, I18nService i18n) {
        this.siteService = siteService;
        this.authService = authService;
        this.i18n = i18n;

        nameField = new TextField(t("sites.field.name"));
        weatherPlaceField = new ComboBox<>(t("sites.field.weatherPlace"));
        timezoneField = new ComboBox<>(t("sites.field.timezone"));
        typeField = new ComboBox<>(t("sites.field.type"));
        enabledToggle = new Checkbox(t("sites.field.enabled"));
        saveButton = new Button(t("sites.button.create"));
        weatherTimestampField = createReadOnlyField(t("sites.weather.field.time"));
        temperatureField = createReadOnlyField(t("sites.weather.field.temperature"));
        windSpeedField = createReadOnlyField(t("sites.weather.field.windSpeed"));
        humidityField = createReadOnlyField(t("sites.weather.field.humidity"));
        weatherInfoSection = createWeatherInfoSection();

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 title = new H2(t("sites.title"));

        configureGrid();
        configureForm();

        card.add(title, sitesGrid, createFormLayout());
        add(card);

        AccountEntity account = ViewAuthUtils.getAuthenticatedAccount(authService, t("sites.notification.sessionExpired"));
        if (account == null) {
            return;
        }
        accountId = account.getId();

        loadSites();
    }

    private void configureForm() {
        nameField.setWidthFull();
        weatherPlaceField.setWidthFull();
        weatherPlaceField.setPlaceholder(t("sites.field.weatherPlace.placeholder"));
        weatherPlaceField.setItems(siteService.getSupportedWeatherPlaces());
        weatherPlaceField.setAllowCustomValue(true);
        weatherPlaceField.setHelperText(t("sites.field.weatherPlace.helper"));
        weatherPlaceField.addCustomValueSetListener(event -> {
            siteService.getSupportedWeatherPlaces().stream()
                    .filter(place -> place.equalsIgnoreCase(event.getDetail()))
                    .findFirst()
                    .ifPresentOrElse(weatherPlaceField::setValue, () -> {
                        weatherPlaceField.clear();
                        Notification.show(t("sites.notification.weatherPlaceUnsupported"))
                                .addThemeVariants(NotificationVariant.LUMO_WARNING);
                    });
        });

        timezoneField.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        timezoneField.setValue("Europe/Helsinki");
        timezoneField.setWidthFull();

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
        FormLayout form = new FormLayout(nameField, weatherPlaceField, timezoneField, typeField, enabledToggle);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        VerticalLayout container = new VerticalLayout(form, saveButton, weatherInfoSection);
        container.getStyle().set("margin-top", "20px");

        return container;
    }

    private VerticalLayout createWeatherInfoSection() {
        H3 title = new H3(t("sites.weather.title"));
        title.getStyle().set("margin", "0");

        FormLayout weatherForm = new FormLayout(weatherTimestampField, temperatureField, windSpeedField, humidityField);
        weatherForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("900px", 4)
        );

        VerticalLayout container = new VerticalLayout(title, weatherForm);
        container.setPadding(false);
        container.setSpacing(true);
        container.setVisible(false);
        return container;
    }

    private void configureGrid() {
        sitesGrid.addColumn(SiteResponse::getId).setHeader("ID").setAutoWidth(true);
        sitesGrid.addColumn(SiteResponse::getName).setHeader(t("sites.grid.name")).setAutoWidth(true);
        sitesGrid.addColumn(site -> t("siteType." + site.getType().name()))
                .setHeader(t("sites.grid.type")).setAutoWidth(true);
        sitesGrid.addColumn(SiteResponse::getWeatherPlace)
                .setHeader(t("sites.grid.weatherPlace")).setAutoWidth(true);
        sitesGrid.addColumn(SiteResponse::getTimezone)
                .setHeader(t("sites.grid.timezone")).setAutoWidth(true);
        sitesGrid.addComponentColumn(site -> {
            boolean enabled = site.getEnabled();
            String text = enabled ? t("common.yes") : t("common.no");
            Span badge = new Span(text);
            badge.getElement().getThemeList().add("badge");
            badge.getElement().getThemeList().add(enabled ? "success" : "error");
            return badge;
        }).setHeader(t("sites.grid.enabled")).setAutoWidth(true);

        sitesGrid.setWidthFull();
        sitesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        sitesGrid.asSingleSelect().addValueChangeListener(event -> {
            SiteResponse selected = event.getValue();
            if (selected != null) {
                editingSiteId = selected.getId();
                nameField.setValue(selected.getName());
                if (selected.getWeatherPlace() == null || selected.getWeatherPlace().isBlank()) {
                    weatherPlaceField.clear();
                } else {
                    weatherPlaceField.setValue(selected.getWeatherPlace());
                }
                timezoneField.setValue(selected.getTimezone() != null ? selected.getTimezone() : "Europe/Helsinki");
                typeField.setValue(selected.getType());
                enabledToggle.setValue(selected.getEnabled());
                saveButton.setText(t("sites.button.update"));
                refreshWeatherInfo();
            }
        });
    }

    private void createNewSite() {
        try {
            siteService.createSite(
                    accountId,
                    nameField.getValue(),
                    typeField.getValue(),
                    enabledToggle.getValue(),
                    weatherPlaceField.getValue(),
                    timezoneField.getValue()
            );
            Notification notification = Notification.show(t("sites.notification.created"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            clearForm();
            loadSites();
        } catch (Exception e) {
            Notification.show(e.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateSite() {
        try {
            siteService.updateSite(
                    editingSiteId,
                    nameField.getValue(),
                    typeField.getValue(),
                    enabledToggle.getValue(),
                    weatherPlaceField.getValue(),
                    timezoneField.getValue()
            );
            Notification notification = Notification.show(t("sites.notification.updated"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshWeatherInfo();
            clearForm();
            loadSites();
        } catch (Exception e) {
            Notification.show(e.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void clearForm() {
        editingSiteId = null;
        nameField.clear();
        weatherPlaceField.clear();
        timezoneField.setValue("Europe/Helsinki");
        typeField.clear();
        enabledToggle.setValue(true);
        saveButton.setText(t("sites.button.create"));
        sitesGrid.deselectAll();
        clearWeatherInfo();
        weatherInfoSection.setVisible(false);
    }

    private void loadSites() {
        sitesGrid.setItems(siteService.getAllSites(accountId));
    }

    private TextField createReadOnlyField(String label) {
        TextField field = new TextField(label);
        field.setReadOnly(true);
        field.setWidthFull();
        return field;
    }

    private void refreshWeatherInfo() {
        if (editingSiteId == null) {
            clearWeatherInfo();
            weatherInfoSection.setVisible(false);
            return;
        }

        weatherInfoSection.setVisible(true);

        SiteWeatherForecastPointResponse point = getCurrentWeatherPoint(editingSiteId);
        if (point == null) {
            weatherTimestampField.setValue(t("sites.weather.notAvailable"));
            temperatureField.setValue("-");
            windSpeedField.setValue("-");
            humidityField.setValue("-");
            return;
        }

        weatherTimestampField.setValue(formatInstant(point.getTime(), getEditingSiteTimezone()));
        temperatureField.setValue(formatDecimal(point.getTemperature(), "°C"));
        windSpeedField.setValue(formatDecimal(point.getWindSpeedMs(), "m/s"));
        humidityField.setValue(formatDecimal(point.getHumidity(), "%"));
    }

    private void clearWeatherInfo() {
        weatherTimestampField.clear();
        temperatureField.clear();
        windSpeedField.clear();
        humidityField.clear();
    }

    private SiteWeatherForecastPointResponse getCurrentWeatherPoint(Long siteId) {
        SiteWeatherForecastResponse response = siteService.getStoredSiteWeatherForecast(accountId, siteId, null, null);
        if (response.getPoints() == null || response.getPoints().isEmpty()) {
            return null;
        }

        Instant now = Instant.now();
        return response.getPoints().stream()
                .min((left, right) -> {
                    long leftDistance = Math.abs(left.getTime().toEpochMilli() - now.toEpochMilli());
                    long rightDistance = Math.abs(right.getTime().toEpochMilli() - now.toEpochMilli());
                    return Long.compare(leftDistance, rightDistance);
                })
                .orElse(null);
    }

    private String getEditingSiteTimezone() {
        return Optional.ofNullable(timezoneField.getValue())
                .filter(value -> !value.isBlank())
                .orElse("Europe/Helsinki");
    }

    private String formatInstant(Instant instant, String timezone) {
        if (instant == null) {
            return "-";
        }
        ZoneId zoneId = ZoneId.of(timezone);
        return dateTimeFormatter.format(ZonedDateTime.ofInstant(instant, zoneId));
    }

    private String formatDecimal(BigDecimal value, String suffix) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString() + (suffix == null || suffix.isBlank() ? "" : " " + suffix);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, authService)) {
            return;
        }
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
