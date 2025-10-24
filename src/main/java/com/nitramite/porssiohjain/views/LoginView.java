package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.LoginResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@PageTitle("Pörssiohjain - Login")
@Route("login")
@PermitAll
public class LoginView extends VerticalLayout {

    protected final I18nService i18n;

    @Autowired
    public LoginView(
            AuthService authService,
            I18nService i18n
    ) {
        this.i18n = i18n;

        getStyle().set("display", "flex");
        getStyle().set("flex-direction", "column");
        getStyle().set("align-items", "center");
        getStyle().set("justify-content", "center");
        getStyle().set("min-height", "100vh");
        getStyle().set("overflow", "auto");

        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setMaxWidth("400px");
        formLayout.setPadding(true);
        formLayout.setSpacing(true);
        formLayout.setAlignItems(Alignment.STRETCH);
        formLayout.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        formLayout.getStyle().set("border-radius", "12px");
        formLayout.getStyle().set("padding", "24px");
        formLayout.getStyle().set("background-color", "var(--lumo-base-color)");

        H2 title = new H2(t("login.title"));
        title.getStyle().set("margin-bottom", "0");

        TextField uuidField = new TextField(t("login.field.uuid"));
        PasswordField secretField = new PasswordField(t("login.field.secret"));

        Button loginButton = new Button(t("login.button.login"), event -> {
            try {
                UUID uuid = UUID.fromString(uuidField.getValue().trim());
                String secret = secretField.getValue().trim();

                String ip = VaadinRequest.getCurrent().getRemoteAddr();
                LoginResponse response = authService.login(ip, uuid, secret);

                VaadinSession.getCurrent().setAttribute("token", response.getToken());
                VaadinSession.getCurrent().setAttribute("expiresAt", response.getExpiresAt());

                Notification.show(t("login.notification.success"));
                UI.getCurrent().navigate(HomeView.class);
            } catch (Exception e) {
                Notification.show(t("login.notification.failed", e.getMessage()));
            }
        });
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        formLayout.add(title, uuidField, secretField, loginButton);
        add(formLayout);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}