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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.enums.AccountTier;
import com.nitramite.porssiohjain.services.AccountService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.views.components.Divider;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;
import java.util.Optional;

@PageTitle("Pörssiohjain - Settings")
@Route("settings")
@PermitAll
public class SettingsView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final I18nService i18n;
    private final AccountService accountService;
    private Long accountId;

    private final EmailField emailField;
    private final Checkbox notifyPowerLimitExceeded;
    private final Select<String> localeSelect;

    @Autowired
    public SettingsView(
            AuthService authService,
            I18nService i18n,
            AccountService accountService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.accountService = accountService;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        emailField = new EmailField(t("settings.account.email"));
        emailField.setPlaceholder("user@example.com");
        emailField.setWidthFull();

        localeSelect = new Select<>();
        localeSelect.setLabel(t("settings.locale"));
        localeSelect.setItems("en", "fi");
        localeSelect.setItemLabelGenerator(code -> switch (code) {
            case "fi" -> "Suomi";
            default -> "English";
        });
        localeSelect.setWidthFull();

        notifyPowerLimitExceeded =
                new Checkbox(t("settings.notifications.powerLimitExceeded"));

        Button saveButton = new Button(t("settings.button.save"));
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("padding-top", "24px");

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setMaxWidth("900px");
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 pageTitle = new H2(t("settings.title"));
        pageTitle.getStyle().set("margin-top", "0");

        Button electricityContractsButton = new Button(t("settings.electricityContracts"), e -> UI.getCurrent().navigate(ElectricityContractsView.class));
        electricityContractsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button sitesButton = new Button(t("settings.sites"), e -> UI.getCurrent().navigate(SitesView.class));
        sitesButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button weatherControlsButton = new Button(t("settings.weatherControls"), e -> UI.getCurrent().navigate(WeatherControlsView.class));
        weatherControlsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button resourceSharingButton = new Button(t("settings.resourceSharing"), e -> UI.getCurrent().navigate(ResourceSharingView.class));
        resourceSharingButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        FlexLayout buttonRow = new FlexLayout(
                electricityContractsButton,
                sitesButton,
                weatherControlsButton,
                resourceSharingButton
        );
        buttonRow.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        buttonRow.setWidthFull();
        buttonRow.getStyle().set("gap", "var(--lumo-space-m)");

        AccountEntity account = ViewAuthUtils.getAuthenticatedAccount(authService, t("settings.sessionExpired"));
        if (account == null) {
            return;
        }
        accountId = account.getId();

        H3 tierTitle = new H3(t("settings.account.tier"));
        tierTitle.getStyle().set("margin-top", "16px");
        AccountTier tier = accountService.getTier(accountId);
        Component subscriptionCard = createSubscriptionCard(tier);

        card.add(
                pageTitle,
                tierTitle,
                subscriptionCard,
                Divider.createDivider(),
                createAccountSection(),
                createNotificationSection(),
                saveButton,
                Divider.createDivider(),
                buttonRow
        );

        add(card);

        emailField.setValue(Optional.ofNullable(accountService.getEmail(accountId)).orElse(""));
        notifyPowerLimitExceeded.setValue(accountService.getNotifyPowerLimitExceeded(accountId));
        localeSelect.setValue(accountService.getLocale(accountId));

        saveButton.addClickListener(e -> {
            accountService.updateAccountSettings(
                    accountId,
                    emailField.getValue(),
                    notifyPowerLimitExceeded.getValue(),
                    localeSelect.getValue()
            );

            Locale newLocale = Locale.forLanguageTag(localeSelect.getValue());
            VaadinSession.getCurrent().setAttribute(Locale.class, newLocale);
            UI.getCurrent().setLocale(newLocale);

            Notification notification = Notification.show(t("settings.saved"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
    }

    private Component createAccountSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        H3 title = new H3(t("settings.account.title"));
        title.getStyle().set("margin-top", "16px");
        FormLayout form = new FormLayout(emailField, localeSelect);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );
        section.add(title, form);
        return section;
    }

    private String getTierDescription(AccountTier tier) {
        return switch (tier) {
            case FREE -> t("settings.account.freeTier");
            case PRO -> t("settings.account.proTier");
            case BUSINESS -> t("settings.account.businessTier");
        };
    }

    private Component createSubscriptionCard(AccountTier tier) {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.setWidth("320px");
        card.getStyle()
                .set("border-radius", "14px")
                .set("box-shadow", "0 6px 18px rgba(0,0,0,0.08)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("background", "var(--lumo-base-color)");
        H4 tierTitle = new H4(tier.name());
        tierTitle.getStyle()
                .set("margin", "0")
                .set("font-weight", "700");
        Span description = new Span(getTierDescription(tier));
        description.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "0.9rem");
        Span badge = new Span("ACTIVE");
        badge.getStyle()
                .set("padding", "3px 10px")
                .set("border-radius", "10px")
                .set("font-size", "0.75rem")
                .set("font-weight", "600")
                .set("color", "white");
        switch (tier) {
            case FREE -> badge.getStyle().set("background", "#6c757d");
            case PRO -> badge.getStyle().set("background", "#0d6efd");
            case BUSINESS -> badge.getStyle().set("background", "#198754");
        }
        Button manageButton = new Button(t("settings.account.managePlan"));
        manageButton.setEnabled(false);
        manageButton.getStyle()
                .set("margin-top", "12px");
        HorizontalLayout header = new HorizontalLayout(tierTitle, badge);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        card.add(header, description, manageButton);
        return card;
    }

    private Component createTierBadge(AccountTier tier) {
        Span badge = new Span(tier.name());
        badge.getStyle()
                .set("padding", "4px 10px")
                .set("border-radius", "12px")
                .set("font-weight", "600")
                .set("font-size", "0.8rem")
                .set("color", "white");
        switch (tier) {
            case FREE -> badge.getStyle().set("background", "#6c757d");
            case PRO -> badge.getStyle().set("background", "#0d6efd");
            case BUSINESS -> badge.getStyle().set("background", "#198754");
        }
        return badge;
    }

    private Component createNotificationSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        H2 title = new H2(t("settings.notifications.title"));
        title.getStyle().set("margin-top", "16px");
        section.add(title, notifyPowerLimitExceeded);
        return section;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, authService)) {
            return;
        }
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}
