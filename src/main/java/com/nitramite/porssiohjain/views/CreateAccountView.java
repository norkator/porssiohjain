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
import com.nitramite.porssiohjain.entity.enums.AccountActivitySource;
import com.nitramite.porssiohjain.services.AccountService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.TermsOfServiceService;
import com.nitramite.porssiohjain.views.components.PublicAccessWindow;
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
import java.util.Locale;

@PageTitle("Pörssiohjain - Create account")
@Route("createAccount")
public class CreateAccountView extends VerticalLayout {

    private final AccountService accountService;
    protected final I18nService i18n;
    private final TermsOfServiceService termsOfServiceService;

    private final Button createButton;
    private final VerticalLayout resultLayout = new VerticalLayout();

    @Autowired
    public CreateAccountView(
            AccountService accountService,
            I18nService i18n,
            TermsOfServiceService termsOfServiceService
    ) {
        this.accountService = accountService;
        this.i18n = i18n;
        this.termsOfServiceService = termsOfServiceService;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        } else {
            Locale defaultLocale = Locale.of("fi", "FI");
            UI.getCurrent().setLocale(defaultLocale);
            VaadinSession.getCurrent().setAttribute(Locale.class, defaultLocale);
        }

        createButton = new Button(t("createAccount.button.create"));

        addClassNames("public-access-view", "public-access-create");
        setPadding(false);
        setSpacing(false);

        VerticalLayout contentBox = new VerticalLayout();

        H1 title = new H1(t("createAccount.title"));

        Paragraph description = new Paragraph(t("createAccount.description"));
        description.addClassName("public-access-description");

        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> openTermsDialog());

        resultLayout.setSpacing(false);
        resultLayout.setPadding(false);
        resultLayout.setVisible(false);
        resultLayout.addClassName("public-access-result");
        resultLayout.getElement().setAttribute("aria-live", "polite");

        Button backButton = new Button(t("createAccount.button.back"), event ->
                UI.getCurrent().navigate(HomeView.class)
        );
        backButton.addClassName("public-access-back");

        contentBox.add(title, description, createButton, resultLayout, backButton);
        add(new PublicAccessWindow(t("createAccount.button.create"), contentBox));
    }

    private void handleCreateAccount() {
        try {
            String ip = VaadinRequest.getCurrent().getRemoteAddr();
            AccountEntity account = accountService.createAccount(ip, true, null, AccountActivitySource.VAADIN);
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
        dialog.addClassName("public-access-dialog");
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
        scrollContainer.addClassName("public-access-terms");
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
        return termsOfServiceService.loadTermsMarkdown(UI.getCurrent().getLocale());
    }

    private void showAccountInfo(AccountEntity account) {
        resultLayout.removeAll();
        resultLayout.setVisible(true);

        H3 successTitle = new H3(t("createAccount.success.title"));

        Div infoBox = new Div();
        infoBox.addClassName("public-access-credentials");

        infoBox.add(
                new Paragraph(t("createAccount.info.uuid", account.getUuid())),
                new Paragraph(t("createAccount.info.secret", account.getSecret()))
        );

        Paragraph note = new Paragraph("⚠️" + t("createAccount.note"));
        note.addClassName("public-access-notice");

        Button loginButton = new Button(t("login.button.login"), event -> UI.getCurrent().navigate(LoginView.class));
        resultLayout.add(successTitle, infoBox, note, loginButton);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
