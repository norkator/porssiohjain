package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.PowerLimitService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitDeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Locale;

@PageTitle("Pörssiohjain - Power Limit")
@Route("power-limit/:powerLimitId")
@PermitAll
public class PowerLimitView extends VerticalLayout implements BeforeEnterObserver {

    private final I18nService i18n;
    private final AuthService authService;
    private final PowerLimitService powerLimitService;
    private final DeviceService deviceService;

    private Long powerLimitId;

    private final Grid<PowerLimitDeviceResponse> deviceGrid = new Grid<>(PowerLimitDeviceResponse.class, false);

    @Autowired
    public PowerLimitView(
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

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            event.forwardTo(LoginView.class);
            return;
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
            return;
        }

        buildView();
    }

    private void buildView() {
        add(new H3(t("powerlimit.title.modify")));

        Paragraph subtitle = new Paragraph(t("powerlimit.subtitle"));
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(subtitle);

        PowerLimitResponse powerLimit = powerLimitService.getPowerLimit(getAccountId(), powerLimitId);

        add(createPowerLimitInfoSection(powerLimit));
        add(createCurrentKwSection(powerLimit));

        configureDeviceGrid();
        loadPowerLimitDevices();

        add(deviceGrid);
        add(createAddDeviceLayout());
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    private void configureDeviceGrid() {
        deviceGrid.removeAllColumns();

        deviceGrid.addColumn(d -> d.getDevice().getDeviceName())
                .setHeader(t("controlTable.grid.deviceName"));

        deviceGrid.addColumn(PowerLimitDeviceResponse::getDeviceChannel)
                .setHeader(t("controlTable.grid.channel"));

        deviceGrid.addColumn(d -> d.getDevice().getUuid())
                .setHeader(t("controlTable.grid.uuid"));

        deviceGrid.addComponentColumn(d -> {
            Button delete = new Button(t("controlTable.button.delete"), e -> {
                powerLimitService.deletePowerLimitDevice(getAccountId(), d.getId());
                loadPowerLimitDevices();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return delete;
        }).setHeader(t("controlTable.grid.actions"));

        deviceGrid.setMaxHeight("250px");
    }


    private void loadPowerLimitDevices() {
        deviceGrid.setItems(powerLimitService.getPowerLimitDevices(powerLimitId));
    }

    private Component createAddDeviceLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>(t("controlTable.deviceSelect"));
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        deviceSelect.setItems(deviceService.getAllDevicesForPowerLimitId(powerLimitId));
        deviceSelect.setWidthFull();

        NumberField channelField = new NumberField(t("controlTable.field.channel"));
        channelField.setStep(1);
        channelField.setWidthFull();

        Button addButton = new Button(t("controlTable.button.addDevice"), e -> {
            if (deviceSelect.getValue() != null && channelField.getValue() != null) {
                powerLimitService.addDeviceToPowerLimit(
                        getAccountId(),
                        powerLimitId,
                        deviceSelect.getValue().getId(),
                        channelField.getValue().intValue()
                );
                loadPowerLimitDevices();
            }
        });
        addButton.setWidthFull();

        FormLayout formLayout = new FormLayout(deviceSelect, channelField, addButton);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 3)
        );

        formLayout.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        return formLayout;
    }

    private Long getAccountId() {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification.show(t("controlTable.sessionExpired"));
            UI.getCurrent().navigate(LoginView.class);
        }

        AccountEntity account = authService.authenticate(token);
        return account.getId();
    }

    private Component createPowerLimitInfoSection(PowerLimitResponse p) {

        TextField nameField = new TextField(t("powerlimit.field.name"));
        nameField.setValue(p.getName());
        nameField.setWidthFull();

        NumberField limitKwField = new NumberField(t("powerlimit.field.limitKw"));
        limitKwField.setStep(0.1);
        limitKwField.setValue(p.getLimitKw().doubleValue());
        limitKwField.setWidthFull();

        Checkbox enabledField = new Checkbox(t("powerlimit.field.enabled"));
        enabledField.setValue(p.isEnabled());

        ComboBox<String> timezoneField = new ComboBox<>(t("powerlimit.field.timezone"));
        timezoneField.setItems(ZoneId.getAvailableZoneIds());
        timezoneField.setValue(p.getTimezone());
        timezoneField.setWidthFull();

        Button saveButton = new Button(t("powerlimit.button.save"), e -> {
            powerLimitService.updatePowerLimit(
                    getAccountId(),
                    p.getId(),
                    nameField.getValue(),
                    BigDecimal.valueOf(limitKwField.getValue()),
                    enabledField.getValue(),
                    timezoneField.getValue()
            );

            Notification.show(t("powerlimit.notification.saved"));
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Div formDiv = new Div();

        FormLayout form = new FormLayout(
                nameField,
                limitKwField,
                enabledField,
                timezoneField
        );

        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        formDiv.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        formDiv.add(form, saveButton);
        return formDiv;
    }

    private Component createCurrentKwSection(PowerLimitResponse p) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("text-align", "center");

        H2 title = new H2(t("powerlimit.currentUsage"));
        title.getStyle().set("margin", "0");

        H1 current = new H1(p.getCurrentKw() + " kW");
        current.getStyle()
                .set("font-size", "3rem")
                .set("font-weight", "bold")
                .set("margin", "0");

        wrapper.add(title, current);
        return wrapper;
    }


}
