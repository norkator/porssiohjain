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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

import java.util.Locale;

@PageTitle("Pörssiohjain - Documentation")
@Route("docs")
@PermitAll
public class DocumentationView extends VerticalLayout {

    private final I18nService i18n;

    public DocumentationView(I18nService i18n) {
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            getUI().ifPresent(ui -> ui.setLocale(storedLocale));
        }

        setWidthFull();
        setAlignItems(Alignment.CENTER);
        getStyle()
                .set("padding", "32px 16px")
                .set("min-height", "100vh")
                .set("box-sizing", "border-box");

        VerticalLayout card = new VerticalLayout();
        card.setMaxWidth("920px");
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "18px")
                .set("box-shadow", "0 10px 28px rgba(0,0,0,0.08)")
                .set("padding", "32px");

        H1 title = new H1(t("docs.title"));
        title.getStyle().set("margin", "0");

        Paragraph intro = new Paragraph(t("docs.intro"));
        intro.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)");

        HorizontalLayout actions = new HorizontalLayout();
        Anchor homeLink = new Anchor("/", "");
        Button homeButton = new Button(t("docs.backHome"));
        homeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        homeLink.add(homeButton);
        actions.add(homeLink);

        Div docsGrid = new Div();
        docsGrid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(240px, 1fr))")
                .set("gap", "16px")
                .set("margin-top", "8px");

        docsGrid.add(
                createDocCard(
                        t("docs.card.controls.title"),
                        t("docs.card.controls.description"),
                        "/docs/en/controls",
                        "/docs/fi/controls"
                ),
                createDocCard(
                        t("docs.card.production.title"),
                        t("docs.card.production.description"),
                        "/docs/en/own-production",
                        "/docs/fi/own-production"
                ),
                createDocCard(
                        t("docs.card.weather.title"),
                        t("docs.card.weather.description"),
                        "/docs/en/weather-controls",
                        "/docs/fi/weather-controls"
                ),
                createDocCard(
                        t("docs.card.powerLimits.title"),
                        t("docs.card.powerLimits.description"),
                        "/docs/en/power-limits",
                        "/docs/fi/power-limits"
                ),
                createDocCard(
                        t("docs.card.devices.title"),
                        t("docs.card.devices.description"),
                        "/docs/en/devices-and-sites",
                        "/docs/fi/devices-and-sites"
                )
        );

        card.add(title, intro, actions, docsGrid);
        add(card);
    }

    private Div createDocCard(String title, String description, String englishHref, String finnishHref) {
        Div card = new Div();
        card.getStyle()
                .set("padding", "20px")
                .set("border-radius", "14px")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "12px");

        H2 heading = new H2(title);
        heading.getStyle().set("margin", "0").set("font-size", "1.15rem");

        Paragraph text = new Paragraph(description);
        text.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)");

        HorizontalLayout links = new HorizontalLayout();
        links.setSpacing(true);

        Anchor english = new Anchor(englishHref, t("docs.link.english"));
        Anchor finnish = new Anchor(finnishHref, t("docs.link.finnish"));
        links.add(english, finnish);

        card.add(heading, text, links);
        return card;
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}
