package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.models.LoginResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Route("login")
@PermitAll
public class LoginView extends VerticalLayout {

    @Autowired
    public LoginView(AuthService authService) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setWidth("400px");
        formLayout.setPadding(true);
        formLayout.setSpacing(true);
        formLayout.setAlignItems(Alignment.STRETCH);
        formLayout.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        formLayout.getStyle().set("border-radius", "12px");
        formLayout.getStyle().set("padding", "24px");
        formLayout.getStyle().set("background-color", "var(--lumo-base-color)");

        H2 title = new H2("Login");
        title.getStyle().set("margin-bottom", "0");

        TextField uuidField = new TextField("UUID");
        PasswordField secretField = new PasswordField("Secret");

        Button loginButton = new Button("Login", event -> {
            try {
                UUID uuid = UUID.fromString(uuidField.getValue().trim());
                String secret = secretField.getValue().trim();

                LoginResponse response = authService.login(uuid, secret);

                VaadinSession.getCurrent().setAttribute("token", response.getToken());
                VaadinSession.getCurrent().setAttribute("expiresAt", response.getExpiresAt());

                Notification.show("Login successful");
                UI.getCurrent().navigate(HomeView.class);
            } catch (Exception e) {
                Notification.show("Login failed: " + e.getMessage());
            }
        });
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        formLayout.add(title, uuidField, secretField, loginButton);
        add(formLayout);
    }
}