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

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.NordpoolService;
import com.nitramite.porssiohjain.views.components.Divider;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

import java.util.Locale;
import java.util.stream.Stream;

@PageTitle("Pörssiohjain - Home")
@Route("")
@PermitAll
public class HomeView extends VerticalLayout {

    private final AuthService authService;
    protected final I18nService i18n;

    public HomeView(
            NordpoolService nordpoolService,
            AuthService authService,
            I18nService i18n
    ) {
        this.authService = authService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        } else {
            Locale defaultLocale = Locale.of("fi", "FI");
            UI.getCurrent().setLocale(defaultLocale);
            VaadinSession.getCurrent().setAttribute(Locale.class, defaultLocale);
        }

        getStyle().set("display", "flex");
        getStyle().set("flex-direction", "column");
        getStyle().set("align-items", "center");
        getStyle().set("justify-content", "center");
        getStyle().set("min-height", "100vh");
        getStyle().set("overflow", "auto");

        VerticalLayout contentBox = new VerticalLayout();
        contentBox.setMaxWidth("500px");
        contentBox.setPadding(true);
        contentBox.setSpacing(true);
        contentBox.setAlignItems(Alignment.CENTER);
        contentBox.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        contentBox.getStyle().set("border-radius", "12px");
        contentBox.getStyle().set("padding", "32px");
        contentBox.getStyle().set("background-color", "var(--lumo-base-color)");

        H1 title = new H1(t("home.title"));
        title.getStyle().set("margin-top", "0");
        title.getStyle().set("font-size", "1.8em");

        Paragraph subtitle = new Paragraph(t("home.subtitle") + " ™");
        subtitle.getStyle().set("margin-bottom", "1.5em");
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button fiButton = new Button(t("lang.finnish"), e -> switchLocale("fi"));
        Button enButton = new Button(t("lang.english"), e -> switchLocale("en"));
        HorizontalLayout langButtons = new HorizontalLayout(enButton, fiButton);
        langButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Button loginButton = new Button(t("home.login"), e -> UI.getCurrent().navigate(LoginView.class));
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button createAccountButton = new Button(t("home.createAccount"), e -> UI.getCurrent().navigate(CreateAccountView.class));
        createAccountButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        Button documentationButton = new Button(t("home.documentation"), e -> UI.getCurrent().navigate(DocumentationView.class));
        documentationButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button devicesButton = new Button(t("home.myDevices"), e -> UI.getCurrent().navigate(DeviceView.class));
        devicesButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button controlsButton = new Button(t("home.myControls"), e -> UI.getCurrent().navigate(ControlsView.class));
        controlsButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button weatherControlsButton = new Button(t("home.weatherControls"), e -> UI.getCurrent().navigate(WeatherControlsView.class));
        weatherControlsButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button myProductionButton = new Button(t("home.myProduction"), e -> UI.getCurrent().navigate(ProductionSourcesView.class));
        myProductionButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button powerLimitsButton = new Button(t("home.powerLimits"), e -> UI.getCurrent().navigate(PowerLimitsView.class));
        powerLimitsButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button temperatureControlsButton = new Button(t("home.thermostats"), e -> UI.getCurrent().navigate(TemperatureCurveView.class));
        temperatureControlsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button dashboardButton = new Button(t("home.dashboard"), e -> UI.getCurrent().navigate(DashboardView.class));
        dashboardButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button settingsButton = new Button(t("home.settings"), e -> UI.getCurrent().navigate(SettingsView.class));
        settingsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button logoutButton = new Button(t("home.logout"), e -> {
            VaadinSession session = VaadinSession.getCurrent();
            session.setAttribute("token", null);
            session.setAttribute("expiresAt", null);
            Notification notification = Notification.show(t("home.logoutSuccess"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().getPage().setLocation("https://www.porssiohjain.fi");
        });
        logoutButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        Stream.of(
                loginButton, createAccountButton, documentationButton, devicesButton, controlsButton, weatherControlsButton, myProductionButton, powerLimitsButton, temperatureControlsButton,
                dashboardButton, settingsButton, logoutButton
        ).forEach(btn -> {
            btn.getStyle().set("transition", "transform 0.1s ease-in-out");
            btn.getElement().addEventListener("mouseover", e -> btn.getStyle().set("transform", "scale(1.03)"));
            btn.getElement().addEventListener("mouseout", e -> btn.getStyle().remove("transform"));
        });

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        boolean loggedIn = token != null && !token.isBlank();

        contentBox.add(langButtons, title, subtitle);

        if (loggedIn) {
            contentBox.add(
                    devicesButton, controlsButton, weatherControlsButton, myProductionButton, powerLimitsButton,
                    dashboardButton, settingsButton, documentationButton, logoutButton, Divider.createDivider()
            );
        } else {
            contentBox.add(loginButton, createAccountButton, documentationButton);
        }

        Paragraph docLink = new Paragraph(t("home.licenseText") + " ");
        Anchor link = new Anchor("https://github.com/norkator/porssiohjain", t("home.docLink"));
        link.setTarget("_blank");
        docLink.add(link);
        contentBox.add(docLink);

        add(contentBox);
    }

    private void switchLocale(String langTag) {
        Locale newLocale = Locale.forLanguageTag(langTag);
        VaadinSession.getCurrent().setAttribute(Locale.class, newLocale);
        UI.getCurrent().getPage().reload();
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
