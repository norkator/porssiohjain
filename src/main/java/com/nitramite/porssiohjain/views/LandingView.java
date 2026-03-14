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

import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

import java.util.Locale;

@PageTitle("Pörssiohjain – Smart electricity automation")
@Route("")
@PermitAll
public class LandingView extends VerticalLayout {

    protected final I18nService i18n;

    public LandingView(I18nService i18n) {
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        } else {
            Locale defaultLocale = Locale.of("fi", "FI");
            UI.getCurrent().setLocale(defaultLocale);
            VaadinSession.getCurrent().setAttribute(Locale.class, defaultLocale);
        }

        setPadding(false);
        setSpacing(false);
        setSizeFull();
        addClassName("landing-view");
        add(createHeader());
        add(createHeroSection());
        add(createFeaturesSection());
        add(createFooter());
    }

    private Component createHeader() {
        HorizontalLayout navbar = new HorizontalLayout();
        navbar.addClassName("landing-navbar");
        navbar.setWidthFull();
        navbar.setPadding(true);
        navbar.setSpacing(true);
        navbar.getStyle()
                .set("background", "#004d4d")
                .set("color", "white")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.15)");
        H3 logo = new H3("Pörssiohjain 2000 ™");
        logo.getStyle().set("margin", "0").set("color", "white");
        Button fiButton = new Button("FI", e -> switchLocale("fi"));
        fiButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        Button enButton = new Button("EN", e -> switchLocale("en"));
        enButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        Button loginButton = new Button(t("home.login"), e -> UI.getCurrent().navigate(LoginView.class));
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout right = new HorizontalLayout(fiButton, enButton, loginButton);
        right.addClassName("landing-navbar-buttons");
        right.setAlignItems(Alignment.CENTER);
        navbar.add(logo, right);
        navbar.expand(logo);
        navbar.setAlignItems(Alignment.CENTER);
        return navbar;
    }

    private Component createHeroSection() {
        VerticalLayout hero = createCard();
        hero.setAlignItems(Alignment.CENTER);
        H1 title = new H1("Pörssiohjain 2000 ™");
        Paragraph subtitle = new Paragraph(t("landing.subtitle"));
        Button createAccount = new Button(t("home.createAccount"), e -> UI.getCurrent().navigate(CreateAccountView.class));
        createAccount.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        Button login = new Button(t("home.login"), e -> UI.getCurrent().navigate(LoginView.class));
        login.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout buttons = new HorizontalLayout(createAccount, login);
        buttons.setSpacing(true);
        hero.add(title, subtitle, buttons);
        return hero;
    }

    private Component createFeaturesSection() {
        VerticalLayout section = createCard();
        section.setAlignItems(Alignment.CENTER);

        H2 title = new H2(t("landing.features"));

        FlexLayout features = new FlexLayout();
        features.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        features.setJustifyContentMode(JustifyContentMode.CENTER);
        features.setWidthFull();
        features.getStyle().set("gap", "var(--lumo-space-m)");

        features.add(
                feature(VaadinIcon.EURO, t("landing.feature.cheapestHours.title"), t("landing.feature.cheapestHours.text")),
                feature(VaadinIcon.WALLET, t("landing.feature.avoidExpensive.title"), t("landing.feature.avoidExpensive.text")),
                feature(VaadinIcon.BOLT, t("landing.feature.powerLimits.title"), t("landing.feature.powerLimits.text")),
                feature(VaadinIcon.SUN_O, t("landing.feature.selfProduction.title"), t("landing.feature.selfProduction.text")),
                feature(VaadinIcon.MONEY, t("landing.feature.affordable.title"), t("landing.feature.affordable.text"))
        );

        section.add(title, features);
        return section;
    }

    private Component feature(VaadinIcon icon, String title, String text) {
        Icon featureIcon = icon.create();
        featureIcon.setSize("40px");
        featureIcon.getStyle().set("color", "#008080").set("margin-bottom", "0.5rem");

        H3 heading = new H3(title);
        Paragraph description = new Paragraph(text);

        VerticalLayout content = new VerticalLayout(featureIcon, heading, description);
        content.setAlignItems(Alignment.CENTER);
        content.setPadding(false);
        content.setSpacing(false);

        Div card = new Div(content);
        card.setWidth("260px");
        card.getClassNames().add("responsive-card");
        card.getElement().getStyle().set("cursor", "pointer");
        card.getElement().addEventListener("mouseenter", e -> card.getStyle().set("box-shadow", "0 8px 20px rgba(0,0,0,0.2)"));
        card.getElement().addEventListener("mouseleave", e -> card.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)"));
        return card;
    }

    private Component createFooter() {
        VerticalLayout card = createCard();
        Paragraph docLink = new Paragraph(t("home.licenseText") + " ");
        Anchor link = new Anchor("https://github.com/norkator/porssiohjain", t("home.docLink"));
        link.setTarget("_blank");
        docLink.add(link);

        HorizontalLayout footer = new HorizontalLayout(docLink);
        footer.setJustifyContentMode(JustifyContentMode.CENTER);
        footer.setAlignItems(Alignment.CENTER);
        footer.setWidthFull();
        footer.getStyle().set("padding", "2rem").set("gap", "var(--lumo-space-m)");

        card.add(footer);
        return card;
    }

    private VerticalLayout createCard() {
        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        card.getStyle().set("border-radius", "12px");
        card.getStyle().set("padding", "32px");
        card.getStyle().set("background-color", "var(--lumo-base-color)");
        return card;
    }

    private void switchLocale(String langTag) {
        Locale newLocale = Locale.forLanguageTag(langTag);
        VaadinSession.getCurrent().setAttribute(Locale.class, newLocale);
        UI.getCurrent().getPage().reload();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token != null && !token.isBlank()) {
            UI.getCurrent().navigate(HomeView.class);
        }
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}