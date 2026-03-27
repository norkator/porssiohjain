/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.WeatherControlService;
import com.nitramite.porssiohjain.services.models.WeatherControlResponse;
import com.nitramite.porssiohjain.views.components.Divider;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

@PageTitle("Pörssiohjain - Weather Control")
@Route("weather-controls/:weatherControlId")
@PermitAll
public class WeatherControlView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final WeatherControlService weatherControlService;
    protected final I18nService i18n;

    private WeatherControlResponse weatherControl;

    @Autowired
    public WeatherControlView(
            AuthService authService,
            WeatherControlService weatherControlService,
            I18nService i18n
    ) {
        this.authService = authService;
        this.weatherControlService = weatherControlService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        setSpacing(true);
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
            return;
        }

        try {
            AccountEntity account = authService.authenticate(token);
            Long accountId = account.getId();
            Long weatherControlId = Long.valueOf(event.getRouteParameters().get("weatherControlId").orElseThrow());
            weatherControl = weatherControlService.getWeatherControl(accountId, weatherControlId);
            renderView();
        } catch (Exception e) {
            removeAll();
            add(new Paragraph(t("weatherControl.errorLoad", e.getMessage())));
        }
    }

    private void renderView() {
        removeAll();

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 title = new H2(t("weatherControl.detail.title", weatherControl.getName()));
        title.getStyle().set("margin-top", "0");

        Div placeholder = new Div();
        placeholder.setText(t("weatherControl.detail.placeholder"));

        card.add(title, Divider.createDivider(), placeholder);
        add(card);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
