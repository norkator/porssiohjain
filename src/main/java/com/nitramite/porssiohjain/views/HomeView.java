package com.nitramite.porssiohjain.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.util.stream.Stream;

@Route("")
public class HomeView extends VerticalLayout {

    public HomeView() {
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

        H1 title = new H1("Welcome to Pörssiohjain");
        title.getStyle().set("margin-top", "0");
        title.getStyle().set("font-size", "1.8em");

        Paragraph subtitle = new Paragraph("This is the home view");
        subtitle.getStyle().set("margin-bottom", "1.5em");
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button loginButton = new Button("Login", e ->
                UI.getCurrent().navigate(LoginView.class)
        );
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button devicesButton = new Button("My Devices", e ->
                UI.getCurrent().navigate(DeviceView.class)
        );
        devicesButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button controlsButton = new Button("My Controls", e ->
                UI.getCurrent().navigate(ControlView.class)
        );
        
        Stream.of(loginButton, devicesButton, controlsButton).forEach(btn -> {
            btn.getStyle().set("transition", "transform 0.1s ease-in-out");
            btn.getElement().addEventListener("mouseover",
                    e -> btn.getStyle().set("transform", "scale(1.03)"));
            btn.getElement().addEventListener("mouseout",
                    e -> btn.getStyle().remove("transform"));
        });

        contentBox.add(title, subtitle, loginButton, devicesButton, controlsButton);
        add(contentBox);
    }
}