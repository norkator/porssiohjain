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
import com.nitramite.porssiohjain.services.ServiceNoticeService;
import com.nitramite.porssiohjain.services.models.ServiceNoticeResponse;
import com.nitramite.porssiohjain.views.components.PublicAccessWindow;
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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

import java.util.Locale;
import java.util.stream.Stream;

@PageTitle("Pörssiohjain - Home")
@Route("")
@PermitAll
public class HomeView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    protected final I18nService i18n;
    private final ServiceNoticeService serviceNoticeService;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.hasValidSession(authService)) {
            event.forwardTo(DesktopView.class);
        }
    }

    public HomeView(
            NordpoolService nordpoolService,
            AuthService authService,
            I18nService i18n,
            ServiceNoticeService serviceNoticeService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.serviceNoticeService = serviceNoticeService;

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
        addClassNames("public-access-view", "public-access-home");
        setPadding(false);
        setSpacing(false);

        VerticalLayout contentBox = new VerticalLayout();

        H1 title = new H1(t("home.title"));

        Button fiButton = new Button(t("lang.finnish"), e -> switchLocale("fi"));
        Button enButton = new Button(t("lang.english"), e -> switchLocale("en"));
        HorizontalLayout langButtons = new HorizontalLayout(enButton, fiButton);
        langButtons.addClassName("public-access-languages");
        langButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        fiButton.getElement().setAttribute("lang", "fi");
        enButton.getElement().setAttribute("lang", "en");
        fiButton.getElement().setAttribute("aria-pressed", String.valueOf("fi".equals(UI.getCurrent().getLocale().getLanguage())));
        enButton.getElement().setAttribute("aria-pressed", String.valueOf("en".equals(UI.getCurrent().getLocale().getLanguage())));

        Button loginButton = new Button(t("home.login"), e -> UI.getCurrent().navigate(LoginView.class));
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button createAccountButton = new Button(t("home.createAccount"), e -> UI.getCurrent().navigate(CreateAccountView.class));
        createAccountButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

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

        Button heatingPlannerButton = new Button(t("home.heatingPlanner"), e -> UI.getCurrent().navigate(HeatingPlannerView.class));
        heatingPlannerButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button myProductionButton = new Button(t("home.myProduction"), e -> UI.getCurrent().navigate(ProductionSourcesView.class));
        myProductionButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button powerLimitsButton = new Button(t("home.powerLimits"), e -> UI.getCurrent().navigate(PowerLimitsView.class));
        powerLimitsButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button loadSheddingButton = new Button(t("home.loadShedding"), e -> UI.getCurrent().navigate(LoadSheddingView.class));
        loadSheddingButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button powerplantButton = new Button(t("home.powerplant"), e -> UI.getCurrent().navigate(PowerplantView.class));
        powerplantButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button solarAnglePlannerButton = new Button(t("home.solarAnglePlanner"), e -> UI.getCurrent().navigate(SolarAnglePlannerView.class));
        solarAnglePlannerButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button dashboardButton = new Button(t("home.dashboard"), e -> UI.getCurrent().navigate(DashboardView.class));
        dashboardButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button settingsButton = new Button(t("home.settings"), e -> UI.getCurrent().navigate(SettingsView.class));
        settingsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button adminButton = new Button(t("home.admin"), e -> UI.getCurrent().navigate(AdminView.class));
        adminButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button logoutButton = new Button(t("home.logout"), e -> {
            VaadinSession session = VaadinSession.getCurrent();
            ViewAuthUtils.stopImpersonating();
            session.setAttribute("token", null);
            session.setAttribute("expiresAt", null);
            removeAll();
            buildContent();
            Notification notification = Notification.show(t("home.logoutSuccess"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        logoutButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        Button stopImpersonatingButton = new Button(t("home.stopImpersonating"), e -> {
            Long impersonatedAccountId = ViewAuthUtils.getImpersonatedAccountId();
            ViewAuthUtils.stopImpersonating();
            removeAll();
            buildContent();
            Notification notification = Notification.show(t("home.impersonationStopped",
                    impersonatedAccountId != null ? impersonatedAccountId : ""));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        stopImpersonatingButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        var authenticatedAccount = ViewAuthUtils.findAuthenticatedAccount(authService);
        boolean loggedIn = authenticatedAccount != null;
        boolean admin = loggedIn && authenticatedAccount.isAdmin();

        contentBox.add(langButtons, title);
        ServiceNoticeResponse notice = serviceNoticeService.getNotice(UI.getCurrent().getLocale());
        if (notice.active()) {
            contentBox.add(createServiceNotice(notice.text()));
        }

        Anchor googlePlayBadgeLink = new Anchor(
                "https://play.google.com/store/apps/details?id=com.nitramite.energycontroller"
        );
        googlePlayBadgeLink.setTarget("_blank");
        googlePlayBadgeLink.getElement().setAttribute("rel", "noopener noreferrer");

        Image googlePlayBadge = new Image("/get_it_on_google_play_badge.svg", "Get it on Google Play");
        googlePlayBadge.setWidth("147px");
        googlePlayBadge.getStyle()
                .set("max-width", "100%")
                .set("height", "auto")
                .set("display", "block");
        googlePlayBadgeLink.add(googlePlayBadge);

        Paragraph supportProject = new Paragraph(t("home.supportProjectText") + " ");
        Anchor coffeeLink = new Anchor("https://buymeacoffee.com/norkator", "☕ " + t("home.buyMeACoffee"));
        coffeeLink.setTarget("_blank");
        coffeeLink.getElement().setAttribute("rel", "noopener noreferrer");
        coffeeLink.getStyle().set("font-weight", "600");
        supportProject.add(coffeeLink);
        Div support = new Div(googlePlayBadgeLink, supportProject);
        support.addClassName("public-access-support");

        if (loggedIn) {
            configureActionButton(devicesButton, VaadinIcon.DESKTOP);
            configureActionButton(controlsButton, VaadinIcon.SLIDERS);
            configureActionButton(weatherControlsButton, VaadinIcon.CLOUD);
            configureActionButton(heatingPlannerButton, VaadinIcon.FIRE);
            configureActionButton(myProductionButton, VaadinIcon.LIGHTBULB);
            configureActionButton(powerLimitsButton, VaadinIcon.FLASH);
            configureActionButton(loadSheddingButton, VaadinIcon.WARNING);
            configureActionButton(powerplantButton, VaadinIcon.DASHBOARD);
            configureActionButton(solarAnglePlannerButton, VaadinIcon.LIGHTBULB);
            configureActionButton(dashboardButton, VaadinIcon.DASHBOARD);
            configureActionButton(settingsButton, VaadinIcon.COG);
            configureActionButton(logoutButton, VaadinIcon.SIGN_OUT);
            configureActionButton(adminButton, VaadinIcon.SHIELD);
            configureActionButton(stopImpersonatingButton, VaadinIcon.CLOSE_CIRCLE);

            FlexLayout actionGrid = new FlexLayout();
            actionGrid.setWidthFull();
            actionGrid.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            actionGrid.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
            actionGrid.setAlignItems(FlexComponent.Alignment.STRETCH);
            actionGrid.getStyle().set("gap", "0.75rem");

            Stream.of(
                    devicesButton, controlsButton, weatherControlsButton, heatingPlannerButton, myProductionButton, powerLimitsButton, loadSheddingButton, powerplantButton, solarAnglePlannerButton,
                    dashboardButton, settingsButton
            ).forEach(actionGrid::add);
            if (admin) {
                actionGrid.add(adminButton);
            }

            if (ViewAuthUtils.isImpersonating()) {
                Span impersonationNotice = new Span(t("home.impersonatingNotice", authenticatedAccount.getId()));
                impersonationNotice.getStyle()
                        .set("color", "var(--lumo-error-text-color)")
                        .set("font-weight", "600")
                        .set("text-align", "center");
                contentBox.add(impersonationNotice);
            }
            contentBox.add(actionGrid, support, logoutButton);
            if (ViewAuthUtils.isImpersonating()) {
                contentBox.add(stopImpersonatingButton);
            }
        } else {
            Div actions = new Div(loginButton, createAccountButton, mobileAppButton);
            actions.addClassName("public-access-actions");
            contentBox.add(actions, support);
        }

        Paragraph docLink = new Paragraph(t("home.licenseText") + " ");
        Anchor link = new Anchor("https://github.com/norkator/porssiohjain", t("home.docLink"));
        link.setTarget("_blank");
        link.getElement().setAttribute("rel", "noopener noreferrer");
        docLink.add(link);
        docLink.addClassName("public-access-license");
        contentBox.add(docLink);

        add(new PublicAccessWindow(t("home.subtitle"), contentBox));
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

    private Div createServiceNotice(String text) {
        Span label = new Span(t("home.serviceNotice"));
        Paragraph message = new Paragraph(text);
        message.getStyle()
                .set("margin", "0")
                .set("white-space", "pre-wrap");
        Div notice = new Div(label, message);
        notice.addClassName("public-access-notice");
        notice.setWidthFull();
        return notice;
    }

}
