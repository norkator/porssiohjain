package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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
        card.getStyle()
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)")
                .set("border-radius", "12px")
                .set("padding", "32px")
                .set("background-color", "var(--lumo-base-color)");

        H2 pageTitle = new H2(t("settings.title"));
        pageTitle.getStyle().set("margin-top", "0");

        Button electricityContractsButton = new Button(t("settings.electricityContracts"), e -> UI.getCurrent().navigate(ElectricityContractsView.class));
        electricityContractsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button sitesButton = new Button(t("settings.sites"), e -> UI.getCurrent().navigate(SitesView.class));
        sitesButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout buttonRow = new HorizontalLayout(
                electricityContractsButton,
                sitesButton
        );

        card.add(
                pageTitle,
                createAccountSection(),
                createNotificationSection(),
                saveButton,
                Divider.createDivider(),
                buttonRow
        );

        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification notification = Notification.show(t("settings.sessionExpired"));
            notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
            UI.getCurrent().navigate(LoginView.class);
            return;
        }

        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();

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
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
        }
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}
