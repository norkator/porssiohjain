/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/** Empty route that leaves the authenticated desktop visible. */
@Route(value = "desktop", layout = MainLayout.class)
@PageTitle("Pörssiohjain - Desktop")
@PermitAll
public class DesktopView extends Div implements BeforeEnterObserver {
    private final AuthService authService;

    public DesktopView(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, authService);
    }
}
