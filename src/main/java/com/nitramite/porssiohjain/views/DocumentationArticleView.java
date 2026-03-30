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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@PageTitle("Pörssiohjain - Documentation")
@Route("docs/:lang/:slug")
@PermitAll
public class DocumentationArticleView extends VerticalLayout implements BeforeEnterObserver {

    private final I18nService i18n;

    public DocumentationArticleView(I18nService i18n) {
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        setWidthFull();
        setAlignItems(Alignment.CENTER);
        getStyle()
                .set("padding", "32px 16px")
                .set("min-height", "100vh")
                .set("box-sizing", "border-box");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String lang = event.getRouteParameters().get("lang").orElse("en");
        String slug = event.getRouteParameters().get("slug").orElse("");

        if (!lang.equals("en") && !lang.equals("fi")) {
            event.forwardTo(DocumentationView.class);
            return;
        }

        UI.getCurrent().setLocale(lang.equals("fi") ? Locale.of("fi", "FI") : Locale.ENGLISH);

        String markdown = loadMarkdown(lang, slug);
        removeAll();

        if (markdown == null) {
            add(createNotFoundLayout());
            return;
        }

        add(createArticleLayout(markdown));
    }

    private VerticalLayout createArticleLayout(String markdownContent) {
        VerticalLayout card = createCard();

        HorizontalLayout actions = new HorizontalLayout();
        Button backButton = new Button(t("docs.backToDocs"), e -> UI.getCurrent().navigate(DocumentationView.class));
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        actions.add(backButton);

        Markdown markdown = new Markdown(markdownContent);
        markdown.setWidthFull();

        card.add(actions, markdown);
        return card;
    }

    private VerticalLayout createNotFoundLayout() {
        VerticalLayout card = createCard();

        H1 title = new H1(t("docs.notFound.title"));
        title.getStyle().set("margin", "0");

        Paragraph description = new Paragraph(t("docs.notFound.description"));
        description.getStyle().set("margin", "0");

        Button backButton = new Button(t("docs.backToDocs"), e -> UI.getCurrent().navigate(DocumentationView.class));
        backButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(title, description, backButton);
        return card;
    }

    private VerticalLayout createCard() {
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
        return card;
    }

    private String loadMarkdown(String lang, String slug) {
        ClassPathResource resource = new ClassPathResource("static/docs/" + lang + "/" + slug + ".md");
        if (!resource.exists()) {
            return null;
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}
