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

    public LandingView(
            I18nService i18n
    ) {
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
        getStyle().set("padding", "var(--lumo-space-m) var(--lumo-space-l)");
        add(createHeader());
        add(createHeroSection());
        add(createFeaturesSection());
        add(createFooter());
    }

    private Component createHeader() {
        VerticalLayout card = createCard();
        card.setPadding(false);
        card.setSpacing(false);
        card.getStyle().set("background", "var(--lumo-base-color)").set("border-radius", "var(--lumo-border-radius-l)").set("box-shadow", "var(--lumo-box-shadow-xs)");
        H3 logo = new H3("Pörssiohjain 2000 ™");
        Button fiButton = new Button(t("lang.finnish"), e -> switchLocale("fi"));
        Button enButton = new Button(t("lang.english"), e -> switchLocale("en"));
        Button loginButton = new Button(t("home.login"), e -> UI.getCurrent().navigate(LoginView.class));
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout right = new HorizontalLayout(fiButton, enButton, loginButton);
        right.setAlignItems(Alignment.CENTER);
        right.setSpacing(true);
        HorizontalLayout header = new HorizontalLayout(logo, right);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        card.add(header);
        return card;
    }

    private Component createHeroSection() {
        VerticalLayout card = createCard();
        card.setAlignItems(Alignment.CENTER);
        H1 title = new H1("Pörssiohjain 2000 ™");
        Paragraph subtitle = new Paragraph(t("landing.subtitle"));
        Button createAccount = new Button(t("home.createAccount"),
                e -> UI.getCurrent().navigate(CreateAccountView.class));
        createAccount.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        Button login = new Button(t("home.login"),
                e -> UI.getCurrent().navigate(LoginView.class));
        login.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout buttons = new HorizontalLayout(createAccount, login);
        card.add(title, subtitle, buttons);
        return card;
    }

    private Component createFeaturesSection() {
        VerticalLayout card = createCard();
        card.setAlignItems(Alignment.CENTER);
        H2 title = new H2(t("landing.features"));
        FlexLayout features = new FlexLayout();
        features.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        features.setJustifyContentMode(JustifyContentMode.CENTER);
        features.setWidthFull();
        features.getStyle().set("gap", "var(--lumo-space-m)");
        features.add(
                feature(t("landing.feature.cheapestHours.title"), t("landing.feature.cheapestHours.text")),
                feature(t("landing.feature.avoidExpensive.title"), t("landing.feature.avoidExpensive.text")),
                feature(t("landing.feature.powerLimits.title"), t("landing.feature.powerLimits.text")),
                feature(t("landing.feature.selfProduction.title"), t("landing.feature.selfProduction.text")),
                feature(t("landing.feature.affordable.title"), t("landing.feature.affordable.text"))
        );
        card.add(title, features);
        return card;
    }

    private Component feature(String title, String text) {
        H3 heading = new H3(title);
        Paragraph description = new Paragraph(text);
        VerticalLayout content = new VerticalLayout(heading, description);
        content.setPadding(false);
        content.setSpacing(false);
        Div card = new Div(content);
        card.setWidth("260px");
        card.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "var(--lumo-box-shadow-xs)")
                .set("background", "var(--lumo-base-color)");
        card.getStyle().set("transition", "box-shadow 0.2s ease");
        card.getElement().addEventListener("mouseenter",
                e -> card.getStyle()
                        .set("box-shadow", "var(--lumo-box-shadow-s)"));
        card.getElement().addEventListener("mouseleave",
                e -> card.getStyle()
                        .set("box-shadow", "var(--lumo-box-shadow-xs)"));
        return card;
    }

    private Component createFooter() {
        VerticalLayout card = createCard();
        card.setPadding(false);
        card.setSpacing(false);
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "var(--lumo-box-shadow-xs)");
        Paragraph text = new Paragraph("Open source energy automation platform.");
        Anchor github = new Anchor("https://github.com/norkator/porssiohjain", "GitHub");
        github.setTarget("_blank");

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
        // card.setMaxWidth("900px");
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