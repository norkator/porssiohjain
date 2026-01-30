package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.PowerLimitService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

@PageTitle("Pörssiohjain - Production Source")
@Route("production-source/:productionSourceId")
@PermitAll
public class ProductionSourceView extends VerticalLayout {

    private final I18nService i18n;
    private final AuthService authService;
    private final PowerLimitService powerLimitService;
    private final DeviceService deviceService;

    @Autowired
    public ProductionSourceView(
            AuthService authService,
            I18nService i18n,
            PowerLimitService powerLimitService,
            DeviceService deviceService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.powerLimitService = powerLimitService;
        this.deviceService = deviceService;

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

        authService.authenticate(token);

        setSizeFull();
        setSpacing(true);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
