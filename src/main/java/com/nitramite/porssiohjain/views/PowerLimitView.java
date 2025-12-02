package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

@PageTitle("Pörssiohjain - Power Limit")
@Route("power-limit/:powerLimitId")
@PermitAll
public class PowerLimitView extends VerticalLayout implements BeforeEnterObserver {

    private final I18nService i18n;
    private Long powerLimitId;

    @Autowired
    public PowerLimitView(
            AuthService authService,
            I18nService i18n
    ) {
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification.show(t("powerlimit.notification.sessionExpired"));
            UI.getCurrent().navigate(LoginView.class);
            return;
        }

        AccountEntity account = authService.authenticate(token);
    }


    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
        }

        String idParam = event.getRouteParameters().get("powerLimitId").orElse(null);
        if (idParam == null) {
            event.forwardTo(PowerLimitsView.class);
            return;
        }

        try {
            powerLimitId = Long.parseLong(idParam);
        } catch (NumberFormatException e) {
            event.forwardTo(PowerLimitsView.class);
        }
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}