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

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.NordpoolService;
import com.nitramite.porssiohjain.views.components.Divider;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
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

        buildContent();
    }

    private void buildContent() {
        getStyle().set("display", "flex");
        getStyle().set("flex-direction", "column");
        getStyle().set("align-items", "center");
        getStyle().set("justify-content", "center");
        getStyle().set("min-height", "100vh");
        getStyle().set("overflow", "auto");

        VerticalLayout contentBox = new VerticalLayout();
        contentBox.setMaxWidth("760px");
        contentBox.setWidthFull();
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

        Button mobileAppButton = new Button(t("home.mobileApp"), e ->
                UI.getCurrent().getPage().open("https://mobile.porssiohjain.fi/", "_blank")
        );
        mobileAppButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

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

        Button loadSheddingButton = new Button(t("home.loadShedding"), e -> UI.getCurrent().navigate(LoadSheddingView.class));
        loadSheddingButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button dashboardButton = new Button(t("home.dashboard"), e -> UI.getCurrent().navigate(DashboardView.class));
        dashboardButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button settingsButton = new Button(t("home.settings"), e -> UI.getCurrent().navigate(SettingsView.class));
        settingsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button adminButton = new Button(t("home.admin"), e -> UI.getCurrent().navigate(AdminView.class));
        adminButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button logoutButton = new Button(t("home.logout"), e -> {
            VaadinSession session = VaadinSession.getCurrent();
            session.setAttribute("token", null);
            session.setAttribute("expiresAt", null);
            removeAll();
            buildContent();
            Notification notification = Notification.show(t("home.logoutSuccess"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        logoutButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        Stream.of(
                loginButton, createAccountButton, documentationButton, devicesButton, controlsButton, weatherControlsButton, loadSheddingButton, myProductionButton, powerLimitsButton,
                dashboardButton, settingsButton, adminButton, logoutButton
        ).forEach(btn -> {
            btn.getStyle().set("transition", "transform 0.1s ease-in-out");
            btn.getElement().addEventListener("mouseover", e -> btn.getStyle().set("transform", "scale(1.03)"));
            btn.getElement().addEventListener("mouseout", e -> btn.getStyle().remove("transform"));
        });

        var authenticatedAccount = ViewAuthUtils.findAuthenticatedAccount(authService);
        boolean loggedIn = authenticatedAccount != null;
        boolean admin = loggedIn && authenticatedAccount.isAdmin();

        contentBox.add(langButtons, title, subtitle);

        if (loggedIn) {
            configureActionButton(devicesButton, VaadinIcon.DESKTOP);
            configureActionButton(controlsButton, VaadinIcon.SLIDERS);
            configureActionButton(weatherControlsButton, VaadinIcon.CLOUD);
            configureActionButton(myProductionButton, VaadinIcon.LIGHTBULB);
            configureActionButton(powerLimitsButton, VaadinIcon.FLASH);
            configureActionButton(loadSheddingButton, VaadinIcon.WARNING);
            configureActionButton(dashboardButton, VaadinIcon.DASHBOARD);
            configureActionButton(settingsButton, VaadinIcon.COG);
            configureActionButton(documentationButton, VaadinIcon.BOOK);
            configureActionButton(logoutButton, VaadinIcon.SIGN_OUT);
            configureActionButton(adminButton, VaadinIcon.SHIELD);

            FlexLayout actionGrid = new FlexLayout();
            actionGrid.setWidthFull();
            actionGrid.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            actionGrid.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
            actionGrid.setAlignItems(FlexComponent.Alignment.STRETCH);
            actionGrid.getStyle().set("gap", "0.75rem");

            Stream.of(
                    devicesButton, controlsButton, weatherControlsButton, myProductionButton, powerLimitsButton, loadSheddingButton,
                    dashboardButton, settingsButton, documentationButton
            ).forEach(actionGrid::add);
            if (admin) {
                actionGrid.add(adminButton);
            }

            contentBox.add(actionGrid, logoutButton);
        } else {
            contentBox.add(loginButton, createAccountButton, mobileAppButton, documentationButton);
        }

        Paragraph docLink = new Paragraph(t("home.licenseText") + " ");
        Anchor link = new Anchor("https://github.com/norkator/porssiohjain", t("home.docLink"));
        link.setTarget("_blank");
        docLink.add(link);
        contentBox.add(Divider.createDivider(), docLink);

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

    private void configureActionButton(Button button, VaadinIcon icon) {
        String labelText = button.getText();
        Icon buttonIcon = icon.create();
        buttonIcon.getStyle()
                .set("width", "1.2rem")
                .set("height", "1.2rem");
        Span label = new Span(labelText);
        label.getStyle().set("white-space", "normal");

        VerticalLayout content = new VerticalLayout(buttonIcon, label);
        content.setPadding(false);
        content.setSpacing(false);
        content.setAlignItems(Alignment.CENTER);
        content.setJustifyContentMode(JustifyContentMode.CENTER);
        content.getStyle()
                .set("gap", "0.2rem")
                .set("width", "100%");

        button.setText("");
        button.getElement().removeAllChildren();
        button.getElement().appendChild(content.getElement());
        button.setWidth("calc(33.333% - 0.5rem)");
        button.getStyle()
                .set("text-align", "center")
                .set("padding", "0.45rem 0.9rem 0.65rem 0.9rem")
                .set("min-width", "150px")
                .set("min-height", "88px")
                .set("line-height", "1.2");
    }

}
