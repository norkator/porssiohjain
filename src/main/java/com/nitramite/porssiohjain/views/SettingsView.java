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
import com.nitramite.porssiohjain.entity.enums.AccountTier;
import com.nitramite.porssiohjain.services.AccountDataExportService;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.AccountService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.nordpool.NordpoolMarket;
import com.nitramite.porssiohjain.views.components.Divider;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Optional;

@PageTitle("Pörssiohjain - Settings")
@Route("settings")
@PermitAll
public class SettingsView extends VerticalLayout implements BeforeEnterObserver {

    private static final String ANDROID_APP_URL = "https://play.google.com/store/apps/details?id=com.nitramite.energycontroller";

    private final AuthService authService;
    private final I18nService i18n;
    private final AccountService accountService;
    private final AccountLimitService accountLimitService;
    private final AccountDataExportService accountDataExportService;
    private Long accountId;

    private final EmailField emailField;
    private final Checkbox notifyPowerLimitExceeded;
    private final Checkbox notifyControlActivated;
    private final Checkbox notifyDeviceOffline;
    private final Checkbox notifyDeviceOnline;
    private final Checkbox emailNotificationsEnabled;
    private final Checkbox pushNotificationsEnabled;
    private final Select<String> localeSelect;
    private final Select<String> marketIndexSelect;
    private final PasswordField currentPasswordField;
    private final PasswordField newPasswordField;
    private final PasswordField confirmNewPasswordField;

    @Autowired
    public SettingsView(
            AuthService authService,
            I18nService i18n,
            AccountService accountService,
            AccountLimitService accountLimitService,
            AccountDataExportService accountDataExportService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.accountService = accountService;
        this.accountLimitService = accountLimitService;
        this.accountDataExportService = accountDataExportService;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        emailField = new EmailField(t("settings.account.email"));
        emailField.setPlaceholder("user@example.com");
        emailField.setWidthFull();

        currentPasswordField = new PasswordField(t("settings.password.current"));
        currentPasswordField.setWidthFull();
        currentPasswordField.setRequiredIndicatorVisible(true);

        newPasswordField = new PasswordField(t("settings.password.new"));
        newPasswordField.setWidthFull();
        newPasswordField.setRequiredIndicatorVisible(true);
        newPasswordField.setHelperText(t("settings.password.requirements"));

        confirmNewPasswordField = new PasswordField(t("settings.password.confirm"));
        confirmNewPasswordField.setWidthFull();
        confirmNewPasswordField.setRequiredIndicatorVisible(true);

        localeSelect = new Select<>();
        localeSelect.setLabel(t("settings.locale"));
        localeSelect.setItems("en", "fi");
        localeSelect.setItemLabelGenerator(code -> switch (code) {
            case "fi" -> "Suomi";
            default -> "English";
        });
        localeSelect.setWidthFull();

        marketIndexSelect = new Select<>();
        marketIndexSelect.setLabel(t("settings.account.market"));
        marketIndexSelect.setItems(NordpoolMarket.SUPPORTED_MARKETS);
        marketIndexSelect.setItemLabelGenerator(this::getMarketLabel);
        marketIndexSelect.setWidthFull();

        notifyPowerLimitExceeded =
                new Checkbox(t("settings.notifications.powerLimitExceeded"));
        notifyControlActivated =
                new Checkbox(t("settings.notifications.controlActivated"));
        notifyDeviceOffline =
                new Checkbox(t("settings.notifications.deviceOffline"));
        notifyDeviceOnline =
                new Checkbox(t("settings.notifications.deviceOnline"));
        emailNotificationsEnabled =
                new Checkbox(t("settings.notifications.emailEnabled"));
        pushNotificationsEnabled =
                new Checkbox(t("settings.notifications.pushEnabled"));

        Button saveButton = new Button(t("settings.button.save"));
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button changePasswordButton = new Button(t("settings.password.change"));
        changePasswordButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

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
        Component limitsCard = createLimitsCard();
        FlexLayout tierCards = new FlexLayout(subscriptionCard, limitsCard);
        tierCards.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        tierCards.setWidthFull();
        tierCards.getStyle()
                .set("gap", "var(--lumo-space-m)")
                .set("align-items", "stretch");

        card.add(
                pageTitle,
                tierTitle,
                tierCards,
                Divider.createDivider(),
                createAccountSection(),
                createPasswordSection(changePasswordButton),
                Divider.createDivider(),
                createNotificationSection(),
                saveButton,
                Divider.createDivider(),
                buttonRow,
                Divider.createDivider(),
                createDataExportSection(),
                Divider.createDivider(),
                createDeleteAccountSection()
        );

        add(card);

        emailField.setValue(Optional.ofNullable(accountService.getEmail(accountId)).orElse(""));
        notifyPowerLimitExceeded.setValue(accountService.getNotifyPowerLimitExceeded(accountId));
        notifyControlActivated.setValue(accountService.getNotifyControlActivated(accountId));
        notifyDeviceOffline.setValue(accountService.getNotifyDeviceOffline(accountId));
        notifyDeviceOnline.setValue(accountService.getNotifyDeviceOnline(accountId));
        emailNotificationsEnabled.setValue(accountService.getEmailNotificationsEnabled(accountId));
        pushNotificationsEnabled.setValue(accountService.getPushNotificationsEnabled(accountId));
        localeSelect.setValue(accountService.getLocale(accountId));
        marketIndexSelect.setValue(accountService.getMarketIndexName(accountId));

        saveButton.addClickListener(e -> {
            accountService.updateAccountSettings(
                    accountId,
                    emailField.getValue(),
                    notifyPowerLimitExceeded.getValue(),
                    notifyControlActivated.getValue(),
                    notifyDeviceOffline.getValue(),
                    notifyDeviceOnline.getValue(),
                    emailNotificationsEnabled.getValue(),
                    pushNotificationsEnabled.getValue(),
                    localeSelect.getValue(),
                    marketIndexSelect.getValue()
            );

            Locale newLocale = Locale.forLanguageTag(localeSelect.getValue());
            VaadinSession.getCurrent().setAttribute(Locale.class, newLocale);
            UI.getCurrent().setLocale(newLocale);

            Notification notification = Notification.show(t("settings.saved"));
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        changePasswordButton.addClickListener(e -> changePassword());
    }

    private Component createAccountSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        H3 title = new H3(t("settings.account.title"));
        title.getStyle().set("margin-top", "16px");
        FormLayout form = new FormLayout(emailField, localeSelect, marketIndexSelect);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );
        section.add(title, form);
        return section;
    }

    private String getMarketLabel(String market) {
        boolean finnish = Locale.forLanguageTag(localeSelect.getValue() != null ? localeSelect.getValue() : "en")
                .getLanguage()
                .equals("fi");
        String name = switch (market) {
            case "AT" -> finnish ? "Itävalta" : "Austria";
            case "BE" -> finnish ? "Belgia" : "Belgium";
            case "BG" -> finnish ? "Bulgaria" : "Bulgaria";
            case "DK1" -> finnish ? "Tanska 1" : "Denmark 1";
            case "DK2" -> finnish ? "Tanska 2" : "Denmark 2";
            case "EE" -> finnish ? "Viro" : "Estonia";
            case "FI" -> finnish ? "Suomi" : "Finland";
            case "FR" -> finnish ? "Ranska" : "France";
            case "GER" -> finnish ? "Saksa" : "Germany";
            case "LT" -> finnish ? "Liettua" : "Lithuania";
            case "LV" -> finnish ? "Latvia" : "Latvia";
            case "NL" -> finnish ? "Alankomaat" : "Netherlands";
            case "NO1" -> finnish ? "Norja 1" : "Norway 1";
            case "NO2" -> finnish ? "Norja 2" : "Norway 2";
            case "NO3" -> finnish ? "Norja 3" : "Norway 3";
            case "NO4" -> finnish ? "Norja 4" : "Norway 4";
            case "NO5" -> finnish ? "Norja 5" : "Norway 5";
            case "PL" -> finnish ? "Puola" : "Poland";
            case "SE1" -> finnish ? "Ruotsi 1" : "Sweden 1";
            case "SE2" -> finnish ? "Ruotsi 2" : "Sweden 2";
            case "SE3" -> finnish ? "Ruotsi 3" : "Sweden 3";
            case "SE4" -> finnish ? "Ruotsi 4" : "Sweden 4";
            case "TEL" -> "TEL";
            default -> market;
        };
        return name + " (" + market + ")";
    }

    private Component createDataExportSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        H3 title = new H3(t("settings.export.title"));
        title.getStyle().set("margin-top", "16px");

        Paragraph description = new Paragraph(t("settings.export.description"));
        description.getStyle().set("margin", "0");

        String filename = "porssiohjain-account-" + accountService.getUuidById(accountId) + "-export.json";
        DownloadHandler downloadHandler = DownloadHandler.fromInputStream(event -> {
            byte[] export = accountDataExportService.exportAccountData(accountId);
            return new DownloadResponse(
                    new ByteArrayInputStream(export),
                    filename,
                    "application/json",
                    export.length
            );
        });

        Anchor downloadLink = new Anchor(downloadHandler, AttachmentType.DOWNLOAD, "");

        Button downloadButton = new Button(t("settings.export.button"));
        downloadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        downloadLink.add(downloadButton);

        section.add(title, description, downloadLink);
        return section;
    }

    private Component createPasswordSection(Button changePasswordButton) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        H3 title = new H3(t("settings.password.title"));
        title.getStyle().set("margin-top", "16px");
        FormLayout form = new FormLayout(currentPasswordField, newPasswordField, confirmNewPasswordField);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );
        section.add(title, form, changePasswordButton);
        return section;
    }

    private Component createDeleteAccountSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        H3 title = new H3(t("settings.delete.title"));
        title.getStyle()
                .set("margin-top", "16px")
                .set("color", "var(--lumo-error-text-color)");

        Paragraph description = new Paragraph(t("settings.delete.description"));
        description.getStyle().set("margin", "0");

        Button deleteButton = new Button(t("settings.delete.button.open"), event -> openDeleteAccountDialog());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        section.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "14px")
                .set("border", "1px solid var(--lumo-error-color-50pct)")
                .set("background", "var(--lumo-error-color-10pct)");

        section.add(title, description, deleteButton);
        return section;
    }

    private void openDeleteAccountDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("settings.delete.dialog.title"));
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        dialog.setWidth("min(640px, 95vw)");

        Paragraph description = new Paragraph(t("settings.delete.dialog.description"));
        description.getStyle().set("margin", "0");

        Button cancelButton = new Button(t("common.cancel"), event -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deleteButton = new Button(t("settings.delete.dialog.confirm"), event -> {
            dialog.close();
            deleteAccount();
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout actions = new HorizontalLayout(cancelButton, deleteButton);
        actions.setWidthFull();
        actions.setJustifyContentMode(JustifyContentMode.END);

        VerticalLayout content = new VerticalLayout(description, actions);
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();

        dialog.add(content);
        dialog.open();
    }

    private void deleteAccount() {
        try {
            accountService.deleteAccount(accountId);

            VaadinSession session = VaadinSession.getCurrent();
            if (session != null) {
                session.setAttribute("token", null);
                session.setAttribute("expiresAt", null);
            }

            UI.getCurrent().navigate(HomeView.class);
        } catch (IllegalArgumentException ex) {
            showNotification(t("settings.delete.failed"), NotificationVariant.LUMO_ERROR);
        }
    }

    private void changePassword() {
        String currentPassword = currentPasswordField.getValue();
        String newPassword = newPasswordField.getValue();
        String confirmNewPassword = confirmNewPasswordField.getValue();

        if (currentPassword == null || currentPassword.isBlank()) {
            showNotification(t("settings.password.currentRequired"), NotificationVariant.LUMO_ERROR);
            return;
        }
        if (!AccountService.isValidSecret(newPassword)) {
            showNotification(t("settings.password.invalid"), NotificationVariant.LUMO_ERROR);
            return;
        }
        if (!newPassword.equals(confirmNewPassword)) {
            showNotification(t("settings.password.mismatch"), NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            boolean changed = accountService.changeSecret(accountId, currentPassword, newPassword);
            if (!changed) {
                showNotification(t("settings.password.currentIncorrect"), NotificationVariant.LUMO_ERROR);
                return;
            }
            currentPasswordField.clear();
            newPasswordField.clear();
            confirmNewPasswordField.clear();
            showNotification(t("settings.password.changed"), NotificationVariant.LUMO_SUCCESS);
        } catch (IllegalArgumentException ex) {
            showNotification(t("settings.password.failed"), NotificationVariant.LUMO_ERROR);
        }
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(variant);
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
        card.setWidth("min(320px, 100%)");
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
        Span managePlanHelp = new Span(t("settings.account.managePlanAndroidApp"));
        managePlanHelp.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "0.85rem")
                .set("margin-top", "12px");

        Button manageButton = new Button(t("settings.account.managePlan"));
        manageButton.getStyle()
                .set("margin-top", "8px");
        Anchor managePlanLink = new Anchor(ANDROID_APP_URL);
        managePlanLink.setTarget("_blank");
        managePlanLink.getElement().setAttribute("rel", "noopener noreferrer");
        managePlanLink.add(manageButton);

        HorizontalLayout header = new HorizontalLayout(tierTitle, badge);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        card.add(header, description, managePlanHelp, managePlanLink);
        return card;
    }

    private Component createLimitsCard() {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.setWidth("min(320px, 100%)");
        card.getStyle()
                .set("border-radius", "14px")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("background", "var(--lumo-contrast-5pct)");

        H4 title = new H4(t("settings.account.limits"));
        title.getStyle().set("margin", "0 0 8px 0");
        Span deviceLimit = new Span(t("settings.account.deviceLimit", accountLimitService.getEffectiveDeviceLimit(accountId)));
        Span controlLimit = new Span(t("settings.account.controlLimit", formatLimit(accountLimitService.getEffectiveControlLimit(accountId))));
        Span productionLimit = new Span(t("settings.account.productionSourceLimit", formatLimit(accountLimitService.getEffectiveProductionSourceLimit(accountId))));
        Span weatherLimit = new Span(t("settings.account.weatherControlLimit", formatLimit(accountLimitService.getEffectiveWeatherControlLimit(accountId))));
        Span weeklyEmailNotificationLimit = new Span(t("settings.account.weeklyEmailNotificationLimit", accountLimitService.getEffectiveWeeklyEmailNotificationLimit(accountId)));
        Span weeklyPushNotificationLimit = new Span(t("settings.account.weeklyPushNotificationLimit", formatLimit(accountLimitService.getEffectiveWeeklyPushNotificationLimit(accountId))));
        card.add(title, deviceLimit, controlLimit, productionLimit, weatherLimit, weeklyEmailNotificationLimit, weeklyPushNotificationLimit);
        return card;
    }

    private String formatLimit(Integer limit) {
        return limit != null ? limit.toString() : t("settings.account.unlimited");
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
        H3 powerLimitTitle = new H3(t("settings.notifications.powerLimitTitle"));
        powerLimitTitle.getStyle().set("margin", "8px 0 0 0");
        H3 controlTitle = new H3(t("settings.notifications.controlTitle"));
        controlTitle.getStyle().set("margin", "8px 0 0 0");
        Span controlHelp = new Span(t("settings.notifications.controlHelp"));
        controlHelp.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        H3 deviceTitle = new H3(t("settings.notifications.deviceTitle"));
        deviceTitle.getStyle().set("margin", "8px 0 0 0");
        Span deviceHelp = new Span(t("settings.notifications.deviceHelp"));
        deviceHelp.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        H3 channelTitle = new H3(t("settings.notifications.channelTitle"));
        channelTitle.getStyle().set("margin", "8px 0 0 0");
        section.add(
                title,
                powerLimitTitle,
                notifyPowerLimitExceeded,
                controlTitle,
                controlHelp,
                notifyControlActivated,
                deviceTitle,
                deviceHelp,
                notifyDeviceOffline,
                notifyDeviceOnline,
                channelTitle,
                emailNotificationsEnabled,
                pushNotificationsEnabled
        );
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
