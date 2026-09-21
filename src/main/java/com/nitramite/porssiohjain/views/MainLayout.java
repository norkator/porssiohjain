/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.views.components.Windows95Header;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.RouterLayout;

/**
 * Common shell for authenticated application views.
 */
public class MainLayout extends Div implements RouterLayout {

    public MainLayout() {
        addClassName("windows-95-main-layout");
        add(new Windows95Header());
    }
}
