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
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/** Shared Windows 98 frame for the public welcome and account views. */
public class PublicAccessWindow extends Div {

    public PublicAccessWindow(String title, VerticalLayout content) {
        addClassName("public-access-window");

        Image titleIcon = new Image("icons/desktop/computer.svg", "");
        titleIcon.addClassName("retro-pixel-icon");
        Div titleBar = new Div(titleIcon, new Span(title));
        titleBar.addClassName("public-access-titlebar");

        Image computer = new Image("icons/desktop/computer.svg", "");
        Span name = new Span("Pörssiohjain");
        Span edition = new Span("98");
        edition.addClassName("public-access-edition");
        Div brand = new Div(computer, name, edition);
        brand.addClassName("public-access-brand");
        brand.getElement().setAttribute("aria-hidden", "true");

        content.addClassName("public-access-content");
        content.setWidthFull();
        content.setPadding(false);
        content.setSpacing(false);
        content.setAlignItems(FlexComponent.Alignment.STRETCH);

        Div main = new Div(brand, content);
        main.addClassName("public-access-main");
        add(titleBar, main);
    }
}
