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

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

@PageTitle("Pörssiohjain – Smart electricity automation")
@Route("")
@PermitAll
public class LandingView extends VerticalLayout {

    public LandingView() {

        setPadding(false);
        setSpacing(false);
        setSizeFull();

        add(createHeader());
        add(createHeroSection());
        add(createFeaturesSection());
        add(createFooter());

    }

    private Component createHeader() {

        H3 logo = new H3("Pörssiohjain");

        Button fiButton = new Button("Suomi");
        Button enButton = new Button("English");

        Button loginButton = new Button("Login", e -> UI.getCurrent().navigate(LoginView.class));
        loginButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button createAccountButton = new Button("Create account", e -> UI.getCurrent().navigate(CreateAccountView.class));
        createAccountButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout right = new HorizontalLayout(fiButton, enButton, loginButton, createAccountButton);
        right.setAlignItems(Alignment.CENTER);
        right.setSpacing(true);

        HorizontalLayout header = new HorizontalLayout(logo, right);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        header.getStyle()
                .set("padding", "1rem 2rem")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        return header;
    }

    private Component createHeroSection() {

        VerticalLayout card = createCard();
        card.setAlignItems(Alignment.CENTER);

        H1 title = new H1("Pörssiohjain");

        Paragraph subtitle = new Paragraph(
                "Automatically optimize electricity usage using Nord Pool prices."
        );

        Button createAccount = new Button("Create account",
                e -> UI.getCurrent().navigate(CreateAccountView.class));
        createAccount.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button login = new Button("Login",
                e -> UI.getCurrent().navigate(LoginView.class));

        HorizontalLayout buttons = new HorizontalLayout(createAccount, login);

        card.add(title, subtitle, buttons);

        return card;
    }

    private Component createFeaturesSection() {

        VerticalLayout card = createCard();
        card.setAlignItems(Alignment.CENTER);

        H2 title = new H2("Features");

        FlexLayout features = new FlexLayout();
        features.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        features.setJustifyContentMode(JustifyContentMode.CENTER);
        features.setWidthFull();

        features.add(
                feature(
                        "Price optimization",
                        "Automatically run devices when electricity prices are lowest."
                ),
                feature(
                        "Nord Pool integration",
                        "Uses real day-ahead electricity prices from Nord Pool."
                ),
                feature(
                        "Device automation",
                        "Control heaters, EV chargers and other smart devices."
                ),
                feature(
                        "Energy monitoring",
                        "Track production, consumption and power limits."
                )
        );

        card.add(title, features);

        return card;
    }

    private Component feature(String title, String text) {

        H3 heading = new H3(title);
        Paragraph description = new Paragraph(text);

        VerticalLayout box = new VerticalLayout(heading, description);
        box.setWidth("260px");
        box.setPadding(true);

        box.getStyle()
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "12px");

        return box;
    }

    private Component createFooter() {

        Paragraph text = new Paragraph(
                "Open source energy automation platform."
        );

        Anchor github = new Anchor(
                "https://github.com/norkator/porssiohjain",
                "GitHub"
        );
        github.setTarget("_blank");

        HorizontalLayout footer = new HorizontalLayout(text, github);
        footer.setJustifyContentMode(JustifyContentMode.CENTER);
        footer.setWidthFull();

        footer.getStyle()
                .set("padding", "2rem")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)");

        return footer;
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

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token != null && !token.isBlank()) {
            UI.getCurrent().navigate(HomeView.class);
        }

    }

}