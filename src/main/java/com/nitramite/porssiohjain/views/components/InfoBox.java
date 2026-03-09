package com.nitramite.porssiohjain.views.components;

import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class InfoBox extends VerticalLayout {

    public InfoBox(
            String title, String description
    ) {
        setPadding(true);
        setSpacing(false);

        getStyle()
                .set("background", "#e6f4ea")
                .set("border", "1px solid #b7e1cd")
                .set("border-radius", "var(--lumo-border-radius-m)");

        H4 header = new H4(title);
        Paragraph text = new Paragraph(description);

        header.getStyle().set("margin", "0");
        text.getStyle().set("margin", "0");

        add(header, text);
    }
}