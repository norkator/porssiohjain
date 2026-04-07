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
import com.nitramite.porssiohjain.services.AccountService;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@PageTitle("Pörssiohjain - Create account")
@Route("createAccount")
public class CreateAccountView extends VerticalLayout {

    private final AccountService accountService;
    protected final I18nService i18n;

    private final Button createButton;
    private final VerticalLayout resultLayout = new VerticalLayout();

    @Autowired
    public CreateAccountView(
            AccountService accountService,
            I18nService i18n
    ) {
        this.accountService = accountService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        } else {
            Locale defaultLocale = Locale.of("fi", "FI");
            UI.getCurrent().setLocale(defaultLocale);
            VaadinSession.getCurrent().setAttribute(Locale.class, defaultLocale);
        }

        createButton = new Button(t("createAccount.button.create"));

        getStyle().set("display", "flex");
        getStyle().set("flex-direction", "column");
        getStyle().set("align-items", "center");
        getStyle().set("justify-content", "center");
        getStyle().set("min-height", "100vh");
        getStyle().set("overflow", "auto");

        VerticalLayout contentBox = new VerticalLayout();
        contentBox.setMaxWidth("600px");
        contentBox.setPadding(true);
        contentBox.setSpacing(true);
        contentBox.setAlignItems(Alignment.CENTER);
        contentBox.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        contentBox.getStyle().set("border-radius", "12px");
        contentBox.getStyle().set("padding", "32px");
        contentBox.getStyle().set("background-color", "var(--lumo-base-color)");

        H1 title = new H1(t("createAccount.title"));
        title.getStyle().set("margin-top", "0");
        title.getStyle().set("font-size", "1.8em");

        Paragraph description = new Paragraph(t("createAccount.description"));
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");
        description.getStyle().set("text-align", "center");

        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> openTermsDialog());

        resultLayout.setSpacing(false);
        resultLayout.setPadding(false);
        resultLayout.setVisible(false);

        Button backButton = new Button(t("createAccount.button.back"), event ->
                UI.getCurrent().getPage().setLocation("https://www.porssiohjain.fi")
        );
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        contentBox.add(title, description, createButton, resultLayout, backButton);
        add(contentBox);
    }

    private void handleCreateAccount() {
        try {
            String ip = VaadinRequest.getCurrent().getRemoteAddr();
            AccountEntity account = accountService.createAccount(ip, true);
            showAccountInfo(account);
            createButton.setEnabled(false);
        } catch (Exception e) {
            Notification notification = Notification.show(t("createAccount.notification.failed", e.getMessage()));
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openTermsDialog() {
        String markdown = loadTermsMarkdown();
        if (markdown == null) {
            Notification notification = Notification.show(t("createAccount.terms.notification.loadFailed"));
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("createAccount.terms.dialog.title"));
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        dialog.setWidth("min(900px, 95vw)");

        Paragraph intro = new Paragraph(t("createAccount.terms.dialog.description"));
        intro.getStyle().set("margin", "0");

        Markdown markdownComponent = new Markdown(markdown);
        markdownComponent.setWidthFull();
        markdownComponent.addClassName("terms-markdown");

        Div scrollContainer = new Div(markdownComponent);
        scrollContainer.setWidthFull();
        scrollContainer.getStyle()
                .set("max-height", "60vh")
                .set("overflow", "auto")
                .set("padding", "0 0.25rem");

        Button cancelButton = new Button(t("common.cancel"), event -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button acceptButton = new Button(t("createAccount.terms.button.accept"), event -> {
            dialog.close();
            handleCreateAccount();
        });
        acceptButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout actions = new HorizontalLayout(cancelButton, acceptButton);
        actions.setWidthFull();
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        VerticalLayout content = new VerticalLayout(intro, scrollContainer, actions);
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();

        dialog.add(content);
        dialog.open();
    }

    private String loadTermsMarkdown() {
        Locale locale = UI.getCurrent().getLocale();
        String lang = locale != null && "fi".equals(locale.getLanguage()) ? "fi" : "en";
        ClassPathResource resource = new ClassPathResource("static/terms/" + lang + "/terms-of-service.md");
        if (!resource.exists()) {
            return null;
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private void showAccountInfo(AccountEntity account) {
        resultLayout.removeAll();
        resultLayout.setVisible(true);

        H3 successTitle = new H3(t("createAccount.success.title"));
        successTitle.getStyle().set("color", "var(--lumo-success-text-color)");
        successTitle.getStyle().set("margin-bottom", "0.5em");

        Div infoBox = new Div();
        infoBox.getStyle()
                .set("padding", "16px")
                .set("border-radius", "8px")
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("font-family", "monospace")
                .set("word-break", "break-all");

        infoBox.add(
                new Paragraph(t("createAccount.info.uuid", account.getUuid())),
                new Paragraph(t("createAccount.info.secret", account.getSecret()))
        );

        Paragraph note = new Paragraph("⚠️" + t("createAccount.note"));
        note.getStyle().set("color", "var(--lumo-error-text-color)");
        note.getStyle().set("margin-top", "1em");

        resultLayout.add(successTitle, infoBox, note);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
