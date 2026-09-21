/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.ServiceNoticeService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@PageTitle("Pörssiohjain - Service Notice")
@Route(value = "admin/service-notice", layout = MainLayout.class)
@PermitAll
public class AdminServiceNoticeView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final I18nService i18n;
    private final ServiceNoticeService serviceNoticeService;

    public AdminServiceNoticeView(
            AuthService authService,
            I18nService i18n,
            ServiceNoticeService serviceNoticeService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.serviceNoticeService = serviceNoticeService;

        var account = ViewAuthUtils.findRealAuthenticatedAccount(authService);
        if (account == null || !account.isAdmin()) {
            return;
        }

        setWidthFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);

        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.addClassName("responsive-card");

        ServiceNoticeService.Configuration configuration = serviceNoticeService.getConfiguration();
        Button backButton = new Button("← " + t("admin.back"),
                event -> UI.getCurrent().navigate(AdminView.class));
        H1 title = new H1(t("admin.serviceNotice.title"));
        Span description = new Span(t("admin.serviceNotice.description"));
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Checkbox active = new Checkbox(t("admin.serviceNotice.active"), configuration.active());
        TextArea finnishText = createTextArea(
                t("admin.serviceNotice.finnishText"), configuration.finnishText());
        TextArea englishText = createTextArea(
                t("admin.serviceNotice.englishText"), configuration.englishText());

        FormLayout fields = new FormLayout(finnishText, englishText);
        fields.setWidthFull();
        fields.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("700px", 2)
        );

        Button save = new Button(t("admin.serviceNotice.save"));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(event -> saveNotice(active, finnishText, englishText));

        card.add(backButton, title, description, active, fields, save);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToHomeIfNotAdmin(event, authService);
    }

    private TextArea createTextArea(String label, String value) {
        TextArea textArea = new TextArea(label);
        textArea.setValue(value);
        textArea.setMaxLength(ServiceNoticeService.MAX_TEXT_LENGTH);
        textArea.setWidthFull();
        textArea.setMinHeight("180px");
        return textArea;
    }

    private void saveNotice(Checkbox active, TextArea finnishText, TextArea englishText) {
        try {
            serviceNoticeService.save(active.getValue(), finnishText.getValue(), englishText.getValue());
            Notification notification = Notification.show(t("admin.serviceNotice.saved"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (IllegalArgumentException exception) {
            Notification notification = Notification.show(
                    t("admin.serviceNotice.saveFailed", exception.getMessage()));
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}
