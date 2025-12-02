package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.PowerLimitService;
import com.nitramite.porssiohjain.services.models.PowerLimitResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@PageTitle("Pörssiohjain - Power Limits")
@Route("power-limits")
@PermitAll
public class PowerLimitsView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<PowerLimitResponse> limitsGrid = new Grid<>(PowerLimitResponse.class, false);
    private final PowerLimitService powerLimitService;
    private final I18nService i18n;

    private Long accountId;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TextField nameField;
    private final NumberField kwLimitField;
    private final Checkbox enabledToggle;
    private final Button createButton;

    @Autowired
    public PowerLimitsView(
            PowerLimitService powerLimitService,
            AuthService authService,
            I18nService i18n
    ) {
        this.powerLimitService = powerLimitService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        nameField = new TextField(t("powerlimit.field.name"));
        kwLimitField = new NumberField(t("powerlimit.field.limitKw"));
        enabledToggle = new Checkbox(t("powerlimit.field.enabled"));
        createButton = new Button(t("powerlimit.button.create"));

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setMaxWidth("1400px");
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)")
                .set("border-radius", "12px")
                .set("padding", "32px")
                .set("background-color", "var(--lumo-base-color)");

        H2 title = new H2(t("powerlimit.title"));
        title.getStyle().set("margin-top", "0");

        configureGrid();
        configureForm();

        card.add(title, limitsGrid, createFormLayout());
        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification.show(t("powerlimit.notification.sessionExpired"));
            UI.getCurrent().navigate(LoginView.class);
            return;
        }

        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();

        loadLimits();
    }

    private void configureForm() {
        nameField.setPlaceholder("Limit name");
        nameField.setWidthFull();

        kwLimitField.setPlaceholder("5.2");
        kwLimitField.setStep(0.1);
        kwLimitField.setMin(0);
        kwLimitField.setWidthFull();

        enabledToggle.setValue(true);

        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> createNewLimit());
    }

    private Component createFormLayout() {
        VerticalLayout container = new VerticalLayout();
        container.setPadding(false);
        container.setSpacing(false);
        container.getStyle()
                .set("margin-top", "20px")
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        FormLayout form = new FormLayout();
        form.add(
                nameField,
                kwLimitField,
                enabledToggle
        );

        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("900px", 3)
        );

        container.add(form, createButton);
        return container;
    }

    private void configureGrid() {
        limitsGrid.addColumn(PowerLimitResponse::getId)
                .setHeader(t("powerlimit.grid.id"))
                .setAutoWidth(true);

        limitsGrid.addColumn(PowerLimitResponse::getName)
                .setHeader(t("powerlimit.grid.name"))
                .setAutoWidth(true);

        limitsGrid.addColumn(PowerLimitResponse::getLimitKw)
                .setHeader(t("powerlimit.grid.limitKw"))
                .setAutoWidth(true);

        limitsGrid.addColumn(PowerLimitResponse::isEnabled)
                .setHeader(t("powerlimit.grid.enabled"))
                .setAutoWidth(true);

        limitsGrid.addColumn(limit -> {
            ZoneId zone = ZoneId.systemDefault();
            return ZonedDateTime.ofInstant(limit.getCreatedAt(), zone).format(formatter);
        }).setHeader(t("powerlimit.grid.created")).setAutoWidth(true);

        limitsGrid.addColumn(limit -> {
            ZoneId zone = ZoneId.systemDefault();
            return ZonedDateTime.ofInstant(limit.getUpdatedAt(), zone).format(formatter);
        }).setHeader(t("powerlimit.grid.updated")).setAutoWidth(true);

        limitsGrid.setWidthFull();
        limitsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        limitsGrid.getStyle().set("max-height", "250px");

        limitsGrid.asSingleSelect().addValueChangeListener(event -> {
            PowerLimitResponse selected = event.getValue();
            if (selected != null) {
                getUI().ifPresent(ui ->
                        ui.navigate(PowerLimitView.class,
                                new RouteParameters("powerLimitId", selected.getId().toString()))
                );
            }
        });
    }

    private void createNewLimit() {
        try {
            String name = nameField.getValue();
            Double kw = kwLimitField.getValue();
            boolean enabled = enabledToggle.getValue();

            if (name == null || name.isBlank()) {
                Notification.show(t("powerlimit.notification.nameEmpty"));
                return;
            }

            if (kw == null) {
                Notification.show(t("powerlimit.notification.numericEmpty"));
                return;
            }

            powerLimitService.createLimit(accountId, name, kw, enabled);
            Notification.show(t("powerlimit.notification.created"));

            clearForm();
            loadLimits();
        } catch (Exception e) {
            Notification.show(t("powerlimit.notification.failed", e.getMessage()));
        }
    }

    private void clearForm() {
        nameField.clear();
        kwLimitField.clear();
        enabledToggle.setValue(true);
    }

    private void loadLimits() {
        try {
            limitsGrid.setItems(powerLimitService.getAllLimits(accountId));
        } catch (Exception e) {
            Notification.show(t("powerlimit.notification.loadFailed", e.getMessage()));
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
        }
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}