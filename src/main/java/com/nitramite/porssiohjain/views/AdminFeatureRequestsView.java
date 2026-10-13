/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.FeatureRequestEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.FeatureRequestService;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Route(value = "admin/feature-requests", layout = MainLayout.class)
@PageTitle("Pörssiohjain - Ideas & needs")
@PermitAll
public class AdminFeatureRequestsView extends VerticalLayout implements BeforeEnterObserver {
    private final AuthService authService;
    private final FeatureRequestService service;
    private final I18nService i18n;
    private final Grid<FeatureRequestEntity> grid = new Grid<>(FeatureRequestEntity.class, false);
    private final Span count = new Span();
    private final Span empty = new Span();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Europe/Helsinki"));
    private final java.util.Map<Long, FeatureRequestConversationDialog> conversations = new java.util.HashMap<>();
    private int page;
    private final Select<String> status = new Select<>();

    public AdminFeatureRequestsView(AuthService authService, FeatureRequestService service, I18nService i18n) {
        this.authService = authService;
        this.service = service;
        this.i18n = i18n;
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null || !account.isAdmin()) return;
        setWidthFull();
        grid.setWidthFull();
        grid.addColumn(row -> time.format(row.getCreatedAt())).setHeader(t("received")).setAutoWidth(true);
        grid.addColumn(row -> i18n.t("featureRequest." + (row.isHandled() ? "handled" : "open"))).setHeader(i18n.t("featureRequest.status"));
        grid.addColumn(FeatureRequestEntity::getAccountId).setHeader(t("account")).setAutoWidth(true);
        grid.addColumn(row -> row.getContactEmail() == null ? "—" : row.getContactEmail()).setHeader(i18n.t("featureRequest.email"));
        grid.addColumn(row -> row.getRequestedChanges().substring(0, Math.min(120, row.getRequestedChanges().length())))
                .setHeader(i18n.t("featureRequest.changes"));
        grid.addComponentColumn(row -> new HorizontalLayout(
                new Button(t("open"), event -> open(row)), deleteButton(row, () -> {}))).setAutoWidth(true);
        previous.setText(i18n.t("featureRequest.back"));
        previous.addClickListener(event -> { page--; refresh(); });
        next.setText(i18n.t("featureRequest.next"));
        next.addClickListener(event -> { page++; refresh(); });
        empty.setText(t("empty"));
        status.setLabel(i18n.t("featureRequest.status"));
        status.setItems("open", "handled", "all");
        status.setItemLabelGenerator(value -> i18n.t("featureRequest." + value));
        status.setValue("open");
        status.addValueChangeListener(event -> { page = 0; refresh(); });
        add(new H1(t("title")), status, new Button(t("refresh"), event -> refresh()),
                empty, grid, new HorizontalLayout(previous, count, next));
        refresh();
    }

    private void refresh() {
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null || !account.isAdmin()) { grid.setItems(java.util.List.of()); return; }
        var results = service.listForAdmin(account.getId(), page, "all".equals(status.getValue()) ? null : "handled".equals(status.getValue()));
        if (page > 0 && results.isEmpty()) { page = Math.max(0, results.getTotalPages() - 1); refresh(); return; }
        grid.setItems(results.getContent());
        empty.setVisible(results.getTotalElements() == 0);
        previous.setEnabled(results.hasPrevious());
        next.setEnabled(results.hasNext());
        count.setText(i18n.t("admin.featureRequests.page", page + 1, Math.max(1, results.getTotalPages()), results.getTotalElements()));
    }

    private void open(FeatureRequestEntity row) {
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null || !account.isAdmin()) return;
        var dialog = conversations.computeIfAbsent(row.getId(), id -> {
            var created = new FeatureRequestConversationDialog(authService, service, i18n, id, true, this::refresh);
            created.getFooter().add(deleteButton(row, created::close));
            add(created);
            return created;
        });
        dialog.open();
    }

    private Button deleteButton(FeatureRequestEntity row, Runnable afterDelete) {
        Button button = new Button(t("delete"), event -> confirmDelete(row, afterDelete));
        button.addThemeVariants(ButtonVariant.LUMO_ERROR);
        return button;
    }

    private void confirmDelete(FeatureRequestEntity row, Runnable afterDelete) {
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null || !account.isAdmin()) return;
        Dialog dialog = new Dialog();
        add(dialog);
        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) remove(dialog);
        });
        dialog.setHeaderTitle(t("deleteConfirmTitle"));
        dialog.add(new Span(i18n.t("admin.featureRequests.deleteConfirmDescription", row.getId())));
        Button delete = new Button(t("delete"), event -> {
            try {
                var currentAccount = ViewAuthUtils.findAuthenticatedAccount(authService);
                service.deleteForAdmin(currentAccount == null ? null : currentAccount.getId(), row.getId());
                dialog.close();
                var conversation = conversations.remove(row.getId());
                if (conversation != null) { conversation.close(); remove(conversation); }
                afterDelete.run();
                refresh();
                Notification.show(t("deleted")).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (IllegalArgumentException exception) {
                Notification.show(t("deleteFailed")).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        delete.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(new Button(i18n.t("common.cancel"), event -> dialog.close()), delete);
        dialog.open();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) { ViewAuthUtils.rerouteToHomeIfNotAdmin(event, authService); }
    private String t(String key) { return i18n.t("admin.featureRequests." + key); }
}
