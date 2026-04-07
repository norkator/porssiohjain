/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.views.components;

import com.vaadin.flow.component.html.Div;

public class Divider {

    public static Div createDivider() {
        Div hr = new Div();
        hr.getStyle().set("width", "100%").set("height", "1px").set("background-color", "var(--lumo-contrast-20pct)").set("margin", "1rem 0");
        return hr;
    }

}
