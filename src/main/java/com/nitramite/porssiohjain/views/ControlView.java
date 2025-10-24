package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlMode;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.ControlResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@PageTitle("Pörssiohjain - Controls")
@Route("controls")
@PermitAll
public class ControlView extends VerticalLayout implements BeforeEnterObserver {

    private final Grid<ControlResponse> controlsGrid = new Grid<>(ControlResponse.class, false);
    private final ControlService controlService;
    protected final I18nService i18n;
    private Long accountId;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TextField nameField;
    private final ComboBox<String> timezoneField;
    private final NumberField maxPriceField;
    private final IntegerField dailyMinutesField;
    private final NumberField taxPercentField;
    private final ComboBox<ControlMode> modeField;
    private final Checkbox manualOnToggle;
    private final Button createButton;

    @Autowired
    public ControlView(
            ControlService controlService,
            AuthService authService,
            I18nService i18n
    ) {
        this.controlService = controlService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        nameField = new TextField(t("control.field.name"));
        timezoneField = new ComboBox<>(t("control.field.timezone"));
        maxPriceField = new NumberField(t("control.field.maxPrice"));
        dailyMinutesField = new IntegerField(t("control.field.dailyMinutes"));
        taxPercentField = new NumberField(t("control.field.taxPercent"));
        modeField = new ComboBox<>(t("control.field.mode"));
        manualOnToggle = new Checkbox(t("control.field.manualOn"));
        createButton = new Button(t("control.button.create"));

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setMaxWidth("1400px");
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.getStyle()
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)")
                .set("border-radius", "12px")
                .set("padding", "32px")
                .set("background-color", "var(--lumo-base-color)");

        H2 title = new H2(t("control.title"));
        title.getStyle().set("margin-top", "0");

        configureGrid();
        configureForm();

        card.add(title, controlsGrid, createFormLayout());
        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification.show(t("control.notification.sessionExpired"));
            UI.getCurrent().navigate(LoginView.class);
            return;
        }

        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();

        loadControls();
    }

    private void configureForm() {
        nameField.setPlaceholder("Enter control name");
        nameField.setWidthFull();

        timezoneField.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        timezoneField.setValue(ZoneId.systemDefault().toString());
        timezoneField.setWidthFull();

        maxPriceField.setPlaceholder("100");
        maxPriceField.setStep(1);
        maxPriceField.setMin(0);
        maxPriceField.setWidthFull();

        dailyMinutesField.setPlaceholder("60");
        dailyMinutesField.setStep(1);
        dailyMinutesField.setMin(0);
        dailyMinutesField.setWidthFull();

        taxPercentField.setPlaceholder("25.5");
        taxPercentField.setValue(25.5);
        taxPercentField.setStep(0.1);
        taxPercentField.setMin(0);
        taxPercentField.setWidthFull();

        modeField.setItems(ControlMode.values());
        modeField.setValue(ControlMode.BELOW_MAX_PRICE);
        modeField.setWidthFull();

        manualOnToggle.setValue(false);
        manualOnToggle.setEnabled(false);

        modeField.addValueChangeListener(e -> {
            ControlMode selected = e.getValue();
            manualOnToggle.setEnabled(selected == ControlMode.MANUAL);
            if (selected != ControlMode.MANUAL) {
                manualOnToggle.setValue(false);
            }
        });

        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> createNewControl());
    }

    private Component createFormLayout() {
        VerticalLayout formContainer = new VerticalLayout();
        formContainer.setPadding(false);
        formContainer.setSpacing(false);
        formContainer.getStyle()
                .set("margin-top", "20px")
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        FormLayout formLayout = new FormLayout();
        formLayout.add(
                nameField,
                timezoneField,
                maxPriceField,
                dailyMinutesField,
                taxPercentField,
                modeField,
                manualOnToggle
        );

        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("900px", 4)
        );

        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.getStyle().set("margin-top", "16px");

        formContainer.add(formLayout, createButton);

        return formContainer;
    }

    private void configureGrid() {
        controlsGrid.addColumn(ControlResponse::getId).setHeader(t("control.grid.id")).setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getName).setHeader(t("control.grid.name")).setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getMaxPriceSnt).setHeader(t("control.grid.maxPrice")).setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getDailyOnMinutes).setHeader(t("control.grid.dailyMinutes")).setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getTimezone).setHeader(t("control.grid.timezone")).setAutoWidth(true);
        controlsGrid.addColumn(ControlResponse::getMode).setHeader(t("control.grid.mode")).setAutoWidth(true);
        controlsGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            return ZonedDateTime.ofInstant(control.getCreatedAt(), zone).format(formatter);
        }).setHeader(t("control.grid.created")).setAutoWidth(true);
        controlsGrid.addColumn(control -> {
            ZoneId zone = ZoneId.of(control.getTimezone());
            return ZonedDateTime.ofInstant(control.getUpdatedAt(), zone).format(formatter);
        }).setHeader(t("control.grid.updated")).setAutoWidth(true);

        controlsGrid.setWidthFull();
        controlsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        controlsGrid.getStyle().set("max-height", "250px");
        controlsGrid.setSelectionMode(Grid.SelectionMode.SINGLE);

        controlsGrid.asSingleSelect().addValueChangeListener(event -> {
            ControlResponse selected = event.getValue();
            if (selected != null) {
                getUI().ifPresent(ui ->
                        ui.navigate(ControlTableView.class,
                                new RouteParameters("controlId", selected.getId().toString()))
                );
            }
        });
    }

    private void createNewControl() {
        try {
            String name = nameField.getValue();
            String timezone = timezoneField.getValue();
            BigDecimal maxPrice = BigDecimal.valueOf(maxPriceField.getValue());
            Integer dailyMinutes = dailyMinutesField.getValue();
            BigDecimal taxPercent = BigDecimal.valueOf(taxPercentField.getValue());
            ControlMode mode = modeField.getValue();
            boolean manualOn = manualOnToggle.getValue();

            if (name == null || name.isBlank()) {
                Notification.show(t("control.notification.nameEmpty"));
                return;
            }

            if (dailyMinutes == null) {
                Notification.show(t("control.notification.numericEmpty"));
                return;
            }


            controlService.createControl(accountId, name, timezone, maxPrice, dailyMinutes, taxPercent, mode, manualOn);
            Notification.show(t("control.notification.created"));

            clearForm();
            loadControls();
        } catch (Exception e) {
            Notification.show(t("control.notification.failed", e.getMessage()));
        }
    }

    private void clearForm() {
        nameField.clear();
        maxPriceField.clear();
        dailyMinutesField.clear();
        taxPercentField.clear();
        modeField.setValue(ControlMode.BELOW_MAX_PRICE);
        manualOnToggle.setValue(false);
        timezoneField.setValue(ZoneId.systemDefault().toString());
    }

    private void loadControls() {
        try {
            controlsGrid.setItems(controlService.getAllControls(accountId));
        } catch (Exception e) {
            Notification.show(t("control.notification.loadFailed", e.getMessage()));
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
