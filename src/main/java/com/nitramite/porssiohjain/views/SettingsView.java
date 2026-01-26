package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AccountService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.EmailService;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@PageTitle("Pörssiohjain - Settings")
@Route("settings")
@PermitAll
public class SettingsView extends VerticalLayout implements BeforeEnterObserver {

    private final I18nService i18n;
    private final AccountService accountService;
    private final EmailService emailService;
    private Long accountId;

    private final EmailField emailField;
    private final Checkbox notifyPowerLimitExceeded;
    private final Button testNotificationButton;
    private final Button saveButton;

    @Autowired
    public SettingsView(
            AuthService authService,
            I18nService i18n,
            AccountService accountService,
            EmailService emailService
    ) {
        this.i18n = i18n;
        this.accountService = accountService;
        this.emailService = emailService;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        emailField = new EmailField(t("settings.account.email"));
        notifyPowerLimitExceeded = new Checkbox(t("settings.notifications.powerLimitExceeded"));
        testNotificationButton = new Button(t("settings.notifications.sendTest"));
        saveButton = new Button(t("settings.button.save"));

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("padding-top", "20px");

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

        H2 title = new H2(t("settings.title"));
        title.getStyle().set("margin-top", "0");

        configureFields();

        card.add(
                title,
                createAccountDetailsSection(),
                createNotificationSettingsSection(),
                saveButton
        );

        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification.show(t("settings.sessionExpired"));
            UI.getCurrent().navigate(LoginView.class);
            return;
        }

        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();

        emailField.setValue(Optional.ofNullable(accountService.getEmail(accountId)).orElse(""));
        notifyPowerLimitExceeded.setValue(accountService.getNotifyPowerLimitExceeded(accountId));
    }

    private void configureFields() {
        emailField.setPlaceholder("user@example.com");
        emailField.setWidthFull();

        notifyPowerLimitExceeded.setValue(false);

        testNotificationButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        // testNotificationButton.addClickListener(e -> {
        //     Notification.show(t("settings.notifications.testSent"));
        //     emailService.sendPowerLimitExceededEmail(
        //             emailField.getValue(), "Test 123", BigDecimal.valueOf(5), BigDecimal.valueOf(6),
        //             Locale.getDefault()
        //     );
        // });

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            accountService.updateAccountSettings(
                    accountId,
                    emailField.getValue(),
                    notifyPowerLimitExceeded.getValue()
            );
            Notification.show(t("settings.saved"));
        });
    }

    private Component createAccountDetailsSection() {
        VerticalLayout container = baseSection();

        H2 title = new H2(t("settings.account.title"));
        title.getStyle().set("margin-top", "0");

        FormLayout form = new FormLayout(emailField);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        container.add(title, form);
        return container;
    }

    private Component createNotificationSettingsSection() {
        VerticalLayout container = baseSection();

        H2 title = new H2(t("settings.notifications.title"));
        title.getStyle().set("margin-top", "0");

        container.add(
                title,
                notifyPowerLimitExceeded,
                testNotificationButton
        );

        return container;
    }

    private VerticalLayout baseSection() {
        VerticalLayout container = new VerticalLayout();
        container.setPadding(false);
        container.setSpacing(false);
        container.getStyle()
                .set("margin-top", "24px")
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");
        return container;
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