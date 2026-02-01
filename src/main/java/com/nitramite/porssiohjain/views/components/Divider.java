package com.nitramite.porssiohjain.views.components;

import com.vaadin.flow.component.html.Div;

public class Divider {

    public static Div createDivider() {
        Div hr = new Div();
        hr.getStyle().set("width", "100%").set("height", "1px").set("background-color", "var(--lumo-contrast-20pct)").set("margin", "1rem 0");
        return hr;
    }

}
