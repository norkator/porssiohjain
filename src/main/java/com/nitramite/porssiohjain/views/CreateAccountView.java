package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AccountService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import org.springframework.beans.factory.annotation.Autowired;

@Route("createAccount")
public class CreateAccountView extends VerticalLayout {

    private final AccountService accountService;

    private final Button createButton = new Button("Create Account");
    private final VerticalLayout resultLayout = new VerticalLayout();

    @Autowired
    public CreateAccountView(AccountService accountService) {
        this.accountService = accountService;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout contentBox = new VerticalLayout();
        contentBox.setWidth("500px");
        contentBox.setPadding(true);
        contentBox.setSpacing(true);
        contentBox.setAlignItems(Alignment.CENTER);
        contentBox.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        contentBox.getStyle().set("border-radius", "12px");
        contentBox.getStyle().set("padding", "32px");
        contentBox.getStyle().set("background-color", "var(--lumo-base-color)");

        H1 title = new H1("Create Pörssiohjain Account");
        title.getStyle().set("margin-top", "0");
        title.getStyle().set("font-size", "1.8em");

        Paragraph description = new Paragraph("""
                    This will create a new account for you automatically.
                    A unique UUID (username) and Secret (password) will be generated.
                    Please save them carefully — they will only be shown once.
                """);
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");
        description.getStyle().set("text-align", "center");

        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> handleCreateAccount());

        resultLayout.setSpacing(false);
        resultLayout.setPadding(false);
        resultLayout.setVisible(false);

        Button backButton = new Button("Back to Home", event -> UI.getCurrent().navigate(HomeView.class));
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        contentBox.add(title, description, createButton, resultLayout, backButton);
        add(contentBox);
    }

    private void handleCreateAccount() {
        try {
            String ip = VaadinRequest.getCurrent().getRemoteAddr();
            AccountEntity account = accountService.createAccount(ip);
            showAccountInfo(account);
            createButton.setEnabled(false);
        } catch (Exception e) {
            Notification.show("Failed to create account: " + e.getMessage());
        }
    }

    private void showAccountInfo(AccountEntity account) {
        resultLayout.removeAll();
        resultLayout.setVisible(true);

        H3 successTitle = new H3("Account Created Successfully!");
        successTitle.getStyle().set("color", "var(--lumo-success-text-color)");
        successTitle.getStyle().set("margin-bottom", "0.5em");

        Div infoBox = new Div();
        infoBox.getStyle()
                .set("padding", "16px")
                .set("border-radius", "8px")
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("font-family", "monospace")
                .set("width", "100%")
                .set("word-break", "break-all");

        infoBox.add(
                new Paragraph("UUID (username): " + account.getUuid()),
                new Paragraph("Secret (password): " + account.getSecret())
        );

        Paragraph note = new Paragraph("""
                    ⚠️ Please copy your UUID and Secret now.
                    They will not be shown again after you leave this page.
                """);
        note.getStyle().set("color", "var(--lumo-error-text-color)");
        note.getStyle().set("margin-top", "1em");

        resultLayout.add(successTitle, infoBox, note);
    }

}