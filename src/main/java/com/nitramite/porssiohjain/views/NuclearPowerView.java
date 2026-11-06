/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/** English-only, per-window simulation. No device commands or persisted telemetry. */
@Route(value = "nuclear-power", layout = MainLayout.class)
@PageTitle("Pörssiohjain - Nuclear Power")
@PermitAll
@JsModule("./nuclear-power.js")
public class NuclearPowerView extends Div implements BeforeEnterObserver {
    private final AuthService authService;

    public NuclearPowerView(AuthService authService) {
        this.authService = authService;
        addClassName("nuclear-power-view");
        getElement().setAttribute("lang", "en");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, authService)) return;
        if (getElement().getChildCount() == 0) {
            getElement().appendChild(new Element("nuclear-power-console"));
        }
    }
}
