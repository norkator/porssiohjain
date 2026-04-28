package com.nitramite.porssiohjain.views.components;

import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class InfoBox extends VerticalLayout {

    public InfoBox(
            String title, String description
    ) {
        addClassName("accent-panel");
        setPadding(true);
        setSpacing(false);

        H4 header = new H4(title);
        Paragraph text = new Paragraph(description);

        header.getStyle().set("margin", "0");
        text.getStyle().set("margin", "0");

        add(header, text);
    }
}
