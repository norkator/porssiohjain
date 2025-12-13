package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.NordpoolService;
import com.nitramite.porssiohjain.services.models.TodayPriceStatsResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.Locale;
import java.util.stream.Stream;

@PageTitle("Pörssiohjain - Home")
@Route("")
@PermitAll
public class HomeView extends VerticalLayout {

    private final AuthService authService;
    protected final I18nService i18n;

    public HomeView(
            NordpoolService nordpoolService,
            AuthService authService,
            I18nService i18n
    ) {
        this.authService = authService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        } else {
            Locale defaultLocale = Locale.of("fi", "FI");
            UI.getCurrent().setLocale(defaultLocale);
            VaadinSession.getCurrent().setAttribute(Locale.class, defaultLocale);
        }

        getStyle().set("display", "flex");
        getStyle().set("flex-direction", "column");
        getStyle().set("align-items", "center");
        getStyle().set("justify-content", "center");
        getStyle().set("min-height", "100vh");
        getStyle().set("overflow", "auto");

        VerticalLayout contentBox = new VerticalLayout();
        contentBox.setMaxWidth("500px");
        contentBox.setPadding(true);
        contentBox.setSpacing(true);
        contentBox.setAlignItems(Alignment.CENTER);
        contentBox.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        contentBox.getStyle().set("border-radius", "12px");
        contentBox.getStyle().set("padding", "32px");
        contentBox.getStyle().set("background-color", "var(--lumo-base-color)");

        H1 title = new H1(t("home.title"));
        title.getStyle().set("margin-top", "0");
        title.getStyle().set("font-size", "1.8em");

        Paragraph subtitle = new Paragraph(t("home.subtitle"));
        subtitle.getStyle().set("margin-bottom", "1.5em");
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button fiButton = new Button(t("lang.finnish"), e -> switchLocale("fi"));
        Button enButton = new Button(t("lang.english"), e -> switchLocale("en"));
        HorizontalLayout langButtons = new HorizontalLayout(enButton, fiButton);
        langButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        HorizontalLayout priceStatsLayout = new HorizontalLayout();
        priceStatsLayout.setWidthFull();
        priceStatsLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        priceStatsLayout.setSpacing(true);
        priceStatsLayout.getStyle().set("flex-wrap", "wrap");

        VerticalLayout minBox = createStatBox(t("home.todayMin"), "–");
        VerticalLayout avgBox = createStatBox(t("home.todayAvg"), "–");
        VerticalLayout maxBox = createStatBox(t("home.todayMax"), "–");

        priceStatsLayout.add(minBox, avgBox, maxBox);

        Button loginButton = new Button(t("home.login"), e -> UI.getCurrent().navigate(LoginView.class));
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button createAccountButton = new Button(t("home.createAccount"), e -> UI.getCurrent().navigate(CreateAccountView.class));
        createAccountButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        Button devicesButton = new Button(t("home.myDevices"), e -> UI.getCurrent().navigate(DeviceView.class));
        devicesButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button controlsButton = new Button(t("home.myControls"), e -> UI.getCurrent().navigate(ControlView.class));

        Button powerLimitsButton = new Button(t("home.powerLimits"), e -> UI.getCurrent().navigate(PowerLimitsView.class));

        Button temperatureControlsButton = new Button("Termostaatit", e -> UI.getCurrent().navigate(TemperatureCurveView.class));

        Button dashboardButton = new Button(t("home.dashboard"), e -> UI.getCurrent().navigate(DashboardView.class));
        dashboardButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button logoutButton = new Button(t("home.logout"), e -> {
            VaadinSession session = VaadinSession.getCurrent();
            session.setAttribute("token", null);
            session.setAttribute("expiresAt", null);
            Notification.show(t("home.logoutSuccess"));
            UI.getCurrent().getPage().reload();
        });
        logoutButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        Stream.of(loginButton, createAccountButton, devicesButton, controlsButton, powerLimitsButton, temperatureControlsButton, dashboardButton, logoutButton).forEach(btn -> {
            btn.getStyle().set("transition", "transform 0.1s ease-in-out");
            btn.getElement().addEventListener("mouseover", e -> btn.getStyle().set("transform", "scale(1.03)"));
            btn.getElement().addEventListener("mouseout", e -> btn.getStyle().remove("transform"));
        });

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        boolean loggedIn = token != null && !token.isBlank();

        contentBox.add(langButtons, title, subtitle);

        if (loggedIn) {
            String timezone = ZoneId.systemDefault().getId();
            TodayPriceStatsResponse stats = nordpoolService.getTodayStats(getAccountId(), timezone);

            updateStatBox(minBox, stats.getMin());
            updateStatBox(avgBox, stats.getAvg());
            updateStatBox(maxBox, stats.getMax());

            contentBox.add(devicesButton, controlsButton, powerLimitsButton, temperatureControlsButton, dashboardButton, logoutButton, createDivider(), priceStatsLayout, createDivider());
        } else {
            contentBox.add(loginButton, createAccountButton);
        }

        Paragraph docLink = new Paragraph(t("home.docText") + " ");
        Anchor link = new Anchor("https://github.com/norkator/porssiohjain", t("home.docLink"));
        link.setTarget("_blank");
        docLink.add(link);
        contentBox.add(docLink);

        add(contentBox);
    }

    private void switchLocale(String langTag) {
        Locale newLocale = Locale.forLanguageTag(langTag);
        VaadinSession.getCurrent().setAttribute(Locale.class, newLocale);
        UI.getCurrent().getPage().reload();
    }

    private VerticalLayout createStatBox(String label, String value) {
        VerticalLayout box = new VerticalLayout();
        box.setAlignItems(Alignment.CENTER);
        box.setPadding(false);
        box.setSpacing(false);
        box.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        box.getStyle().set("border-radius", "8px");
        box.getStyle().set("padding", "12px 20px");
        box.getStyle().set("min-width", "120px");
        box.getStyle().set("max-width", "120px");
        box.getStyle().set("flex", "1 1 auto");
        box.getStyle().set("text-align", "center");

        H2 valueText = new H2(value);
        valueText.getStyle().set("margin", "0");
        valueText.getStyle().set("font-size", "1.2em");

        Span labelText = new Span(label);
        labelText.getStyle().set("font-size", "0.8em");
        labelText.getStyle().set("color", "var(--lumo-secondary-text-color)");

        box.add(valueText, labelText);
        return box;
    }

    private void updateStatBox(VerticalLayout box, BigDecimal value) {
        H2 valueText = (H2) box.getChildren().findFirst().orElse(null);
        if (valueText != null) {
            valueText.setText(value.setScale(2, RoundingMode.HALF_UP) + " c/kWh");
        }
    }

    private Div createDivider() {
        Div hr = new Div();
        hr.getStyle().set("width", "100%").set("height", "1px").set("background-color", "var(--lumo-contrast-20pct)").set("margin", "1rem 0");
        return hr;
    }

    private Long getAccountId() {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification.show(t("home.sessionExpired"));
            UI.getCurrent().navigate(LoginView.class);
        }

        AccountEntity account = authService.authenticate(token);
        return account.getId();
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}

