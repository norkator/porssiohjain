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
import com.nitramite.porssiohjain.services.models.FeatureRequestSummary;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import lombok.extern.slf4j.Slf4j;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;

/** Own request history and a guided form. Closing keeps request and comment drafts. */
@Slf4j
public class FeatureRequestDialog extends Dialog {
    private final AuthService authService;
    private final FeatureRequestService service;
    private final I18nService i18n;
    private final Runnable historyChanged;
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
    private final java.util.Map<Long, FeatureRequestConversationDialog> conversations = new java.util.HashMap<>();
    private int historyPage;
    private final VerticalLayout history = new VerticalLayout();
    private final Grid<FeatureRequestSummary> requests = new Grid<>(FeatureRequestSummary.class, false);
    private final Span historyMessage = new Span();
    private final Button historyBack = new Button();
    private final Button historyNext = new Button();

    public FeatureRequestDialog(AuthService authService, FeatureRequestService service, I18nService i18n) {
        this(authService, service, i18n, () -> {});
    }

    public FeatureRequestDialog(AuthService authService, FeatureRequestService service, I18nService i18n, Runnable historyChanged) {
        this.historyChanged = historyChanged;
        this.authService = authService;
        this.service = service;
        this.i18n = i18n;
        setHeaderTitle(t("title"));
        setWidth("960px");
        setMaxWidth("calc(100vw - 32px)");
        setCloseOnOutsideClick(false);
        history.setPadding(false);
        history.setWidthFull();
        requests.setWidthFull();
        requests.setHeight("240px");
        requests.addColumn(row -> "#" + row.id() + " · " + java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(java.time.ZoneId.of("Europe/Helsinki")).format(row.createdAt())).setHeader(t("request"));
        requests.addColumn(row -> row.requestedChanges().substring(0, Math.min(100, row.requestedChanges().length()))).setHeader(t("changes"));
        requests.addColumn(row -> t(row.handled() ? "handled" : "open")).setHeader(t("status"));
        requests.addComponentColumn(row -> {
            Button button = new Button(t(row.unreadComments() ? "newComment" : "viewRequest"), event -> {
                var dialog = conversations.computeIfAbsent(row.id(), id -> {
                    var created = new FeatureRequestConversationDialog(authService, service, i18n, id, false, this::refreshHistory);
                    add(created);
                    return created;
                });
                dialog.open();
            });
            if (row.unreadComments()) button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            return button;
        });
        historyBack.setText(t("back"));
        historyBack.addClickListener(event -> { historyPage--; refreshHistory(); });
        historyNext.setText(t("next"));
        historyNext.addClickListener(event -> { historyPage++; refreshHistory(); });
        history.add(heading("myRequests"), new Button(t("refresh"), event -> refreshHistory()), historyMessage,
                requests, new HorizontalLayout(historyBack, historyNext));
        add(history, heading("newRequest"));
        addOpenedChangeListener(event -> { if (event.isOpened()) refreshHistory(); });
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
        contact.add(new Span(t("review")), heading("useCase"), useCaseReview,
                heading("changes"), changesReview, email);
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
            DesktopFormState.saved(useCase);
            DesktopFormState.saved(changes);
            DesktopFormState.saved(email);
            step = 0;
            render();
            refreshHistory();
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

    private void refreshHistory() {
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null) return;
        try {
            var results = service.listOwn(account.getId(), historyPage);
            if (historyPage > 0 && results.isEmpty()) { historyPage = Math.max(0, results.getTotalPages() - 1); refreshHistory(); return; }
            requests.setItems(results.getContent());
            historyMessage.setText(results.isEmpty() ? t("noRequests") : "");
            historyBack.setEnabled(results.hasPrevious());
            historyNext.setEnabled(results.hasNext());
            historyChanged.run();
        } catch (Exception exception) { historyMessage.setText(t("loadFailed")); }
    }

    private H3 heading(String key) {
        H3 heading = new H3(t(key));
        heading.addClassName("feature-request-section-title");
        return heading;
    }

    private String t(String key) { return i18n.t("featureRequest." + key); }
}
