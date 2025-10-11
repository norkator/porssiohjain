package com.nitramite.porssiohjain.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("createAccount")
public class CreateAccountView extends VerticalLayout {

    public CreateAccountView() {
        add(new H1("Create Pörssiohjain account"));
        add(new Paragraph("Todo"));
    }

}
