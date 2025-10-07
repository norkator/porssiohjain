package com.nitramite.porssiohjain.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("")
public class HomeView extends VerticalLayout {

    public HomeView() {
        add(new H1("Welcome to your new application"));
        add(new Paragraph("This is the home view"));

        // Navigation buttons
        Button loginButton = new Button("Go to Login", e ->
                UI.getCurrent().navigate(LoginView.class)
        );

        Button controlButton = new Button("Go to Controls", e ->
                UI.getCurrent().navigate(ControlView.class)
        );

        add(loginButton, controlButton);
    }

}
