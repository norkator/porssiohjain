/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 */

package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.views.HomeView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Shared Windows 95 styled navigation bar for application views.
 */
public class Windows95Header extends HorizontalLayout {

    public Windows95Header() {
        addClassName("windows-95-header");
        setWidthFull();
        setPadding(false);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);
        getElement().setAttribute("role", "navigation");
        getElement().setAttribute("aria-label", "Main navigation");

        Button homeButton = new Button("Home", VaadinIcon.HOME.create(),
                event -> UI.getCurrent().navigate(HomeView.class));
        homeButton.addClassName("windows-95-header-button");
        homeButton.getElement().setAttribute("aria-label", "Home");

        add(homeButton);
    }
}
