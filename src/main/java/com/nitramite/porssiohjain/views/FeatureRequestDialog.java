/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.FeatureRequestService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.FeatureRequestInput;
import lombok.extern.slf4j.Slf4j;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;

/** A single guided form for survey responses and feature ideas. Closing keeps the draft. */
@Slf4j
public class FeatureRequestDialog extends Dialog {
    private final AuthService authService;
    private final FeatureRequestService service;
    private final I18nService i18n;
    private final TextArea useCase = new TextArea();
    private final TextArea changes = new TextArea();
    private final EmailField email = new EmailField();
    private final Span progress = new Span();
    private final Span error = new Span();
    private final VerticalLayout contact = new VerticalLayout();
    private final Span useCaseReview = new Span();
    private final Span changesReview = new Span();
    private final Button back;
    private final Button next;
    private final Button send;
    private int step;

    public FeatureRequestDialog(AuthService authService, FeatureRequestService service, I18nService i18n) {
        this.authService = authService;
        this.service = service;
        this.i18n = i18n;
        setHeaderTitle(t("title"));
        setWidth("640px");
        setMaxWidth("calc(100vw - 32px)");
        setCloseOnOutsideClick(false);
        configure(useCase, "useCase", "useCaseHelp");
        configure(changes, "changes", "changesHelp");
        email.setLabel(t("email"));
        email.setHelperText(t("emailHelp"));
        email.setMaxLength(254);
        email.setWidthFull();
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        email.setValue(account != null && account.getEmail() != null ? account.getEmail() : "");
        contact.setPadding(false);
        contact.setWidthFull();
        useCaseReview.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
        changesReview.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
        contact.add(new Span(t("review")), new Span(t("useCase")), useCaseReview,
                new Span(t("changes")), changesReview, email);
        error.getStyle().set("color", "var(--lumo-error-text-color)");
        error.getElement().setAttribute("role", "alert");
        VerticalLayout body = new VerticalLayout(new Span(t("description")), progress, useCase, changes, contact, error);
        body.setPadding(false);
        add(body);
        back = new Button(t("back"), event -> { step--; render(); });
        next = new Button(t("next"), event -> {
            TextArea field = step == 0 ? useCase : changes;
            field.setInvalid(field.getValue().isBlank());
            field.setErrorMessage(t("required"));
            if (!field.isInvalid()) { step++; render(); }
        });
        send = new Button(t("send"), event -> submit());
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (ViewAuthUtils.isImpersonating() || account != null && account.isDemo()) {
            send.setEnabled(false);
            body.add(new Span(t("readOnly")));
        }
        getFooter().add(new Button(t("close"), event -> close()), back, next, send);
        DesktopFormState.watch(body);
        render();
    }

    private void configure(TextArea field, String label, String help) {
        field.setLabel(t(label));
        field.setHelperText(t(help));
        field.setRequiredIndicatorVisible(true);
        field.setMaxLength(5000);
        field.setWidthFull();
        field.setMinHeight("200px");
    }

    private void render() {
        progress.setText(i18n.t("featureRequest.step", step + 1));
        useCase.setVisible(step == 0);
        changes.setVisible(step == 1);
        contact.setVisible(step == 2);
        back.setVisible(step > 0);
        next.setVisible(step < 2);
        send.setVisible(step == 2);
        useCaseReview.setText(useCase.getValue());
        changesReview.setText(changes.getValue());
        error.setText("");
    }

    private void submit() {
        var account = ViewAuthUtils.getAuthenticatedAccount(authService, i18n.t("home.sessionExpired"));
        if (account == null) { close(); return; }
        if (ViewAuthUtils.isImpersonating() || account.isDemo()) { error.setText(t("readOnly")); return; }
        send.setEnabled(false);
        try {
            service.submit(account.getId(), new FeatureRequestInput(useCase.getValue(), changes.getValue(), email.getValue()));
            useCase.clear();
            changes.clear();
            DesktopFormState.saved(this);
            step = 0;
            render();
            close();
            Notification.show(t("sent"));
        } catch (IllegalArgumentException exception) {
            error.setText(t("invalid"));
        } catch (Exception exception) {
            log.error("Failed to save feature request for account {}", account.getId(), exception);
            error.setText(t("failed"));
        } finally {
            send.setEnabled(true);
        }
    }

    private String t(String key) { return i18n.t("featureRequest." + key); }
}
