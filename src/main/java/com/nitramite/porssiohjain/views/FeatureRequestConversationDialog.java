/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.FeatureRequestService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.FeatureRequestDetail;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Read-only original submission with an append-only conversation. */
class FeatureRequestConversationDialog extends Dialog {
    private final AuthService auth;
    private final FeatureRequestService service;
    private final I18nService i18n;
    private final Long requestId;
    private final boolean adminView;
    private final Runnable changed;
    private final VerticalLayout conversation = new VerticalLayout();
    private final TextArea reply = new TextArea();
    private final Span error = new Span();
    private final Button send;
    private final Button handled;
    private boolean isHandled;
    private final DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Helsinki"));

    FeatureRequestConversationDialog(AuthService auth, FeatureRequestService service, I18nService i18n,
                                     Long requestId, boolean adminView, Runnable changed) {
        this.auth = auth;
        this.service = service;
        this.i18n = i18n;
        this.requestId = requestId;
        this.adminView = adminView;
        this.changed = changed;
        setHeaderTitle(t("title") + " #" + requestId);
        setWidth("960px");
        setMaxWidth("calc(100vw - 32px)");
        setCloseOnOutsideClick(false);
        conversation.setPadding(false);
        reply.setLabel(t("reply"));
        reply.setMaxLength(5000);
        reply.setWidthFull();
        error.getElement().setAttribute("role", "alert");
        error.getStyle().set("color", "var(--lumo-error-text-color)");
        send = new Button(t("sendComment"));
        send.addClickListener(event -> {
            if (!writable()) { error.setText(t("readOnly")); return; }
            send.setEnabled(false);
            try {
                service.comment(accountId(), requestId, reply.getValue(), adminView);
                reply.clear();
                DesktopFormState.saved(reply);
                refresh();
            } catch (IllegalArgumentException exception) { error.setText(t("commentInvalid")); }
            catch (Exception exception) { error.setText(t("failed")); }
            finally { send.setEnabled(writable()); }
        });
        handled = new Button("", event -> {
            if (!writable()) { error.setText(t("readOnly")); return; }
            try {
                service.setHandled(accountId(), requestId, !isHandled);
                refresh();
            } catch (Exception exception) { error.setText(t("failed")); }
        });
        handled.setVisible(adminView);
        add(new VerticalLayout(conversation, reply, error));
        getFooter().add(new Button(t("close"), event -> close()), new Button(t("refresh"), event -> refresh()), handled, send);
        DesktopFormState.watch(reply);
    }

    @Override
    public void open() { refresh(); super.open(); }

    private Long accountId() {
        var account = ViewAuthUtils.findAuthenticatedAccount(auth);
        return account == null ? null : account.getId();
    }

    private boolean writable() {
        var account = ViewAuthUtils.findAuthenticatedAccount(auth);
        return account != null && !account.isDemo() && (!adminView || account.isAdmin());
    }

    private void refresh() {
        conversation.removeAll();
        try {
            FeatureRequestDetail detail = service.detail(accountId(), requestId, adminView);
            isHandled = detail.handled();
            conversation.add(new Span(time.format(detail.createdAt()) + " · " + t(isHandled ? "handled" : "open")));
            if (adminView) conversation.add(new Span(t("email") + ": " + (detail.contactEmail() == null ? "—" : detail.contactEmail())));
            conversation.add(heading("useCase"), text(detail.useCase()), heading("changes"), text(detail.requestedChanges()), heading("comments"));
            if (detail.comments().isEmpty()) conversation.add(new Span(t("noComments")));
            for (var comment : detail.comments()) {
                Span author = new Span(t(comment.adminAuthor() ? "adminAuthor" : "userAuthor") + " · " + time.format(comment.createdAt()));
                author.addClassName("feature-request-comment-author");
                VerticalLayout entry = new VerticalLayout(author, text(comment.message()));
                entry.addClassNames("feature-request-comment", comment.adminAuthor() ? "feature-request-comment--admin" : "feature-request-comment--user");
                conversation.add(entry);
            }
            handled.setText(t(isHandled ? "reopen" : "markHandled"));
            handled.setEnabled(writable());
            send.setEnabled(writable());
            reply.setReadOnly(!writable());
            error.setText(writable() ? "" : t("readOnly"));
            if (!adminView && writable()) service.markRead(accountId(), requestId, detail.lastSeenCommentId());
            changed.run();
        } catch (Exception exception) { error.setText(t("loadFailed")); send.setEnabled(false); handled.setEnabled(false); }
    }

    private H3 heading(String key) {
        H3 heading = new H3(t(key));
        heading.addClassName("feature-request-section-title");
        return heading;
    }

    private Span text(String value) {
        Span span = new Span(value);
        span.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
        return span;
    }
    private String t(String key) { return i18n.t("featureRequest." + key); }
}
