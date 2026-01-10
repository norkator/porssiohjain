package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@PageTitle("Pörssiohjain - Electricity Contracts")
@Route("electricity-contracts")
@PermitAll
public class ElectricityContractsView extends VerticalLayout implements BeforeEnterObserver {

    private final I18nService i18n;
    private Long accountId;

    private final TextField nameField;
    private final ComboBox<ContractType> typeField;

    private final NumberField basicFeeField;
    private final NumberField nightPriceField;
    private final NumberField dayPriceField;
    private final NumberField staticPriceField;

    private final NumberField taxPercentField;
    private final NumberField taxAmountField;

    private final Checkbox staticPricingToggle;
    private final Button saveButton;

    private final Grid<ElectricityContract> grid = new Grid<>(ElectricityContract.class, false);
    private final Binder<ElectricityContract> binder = new Binder<>(ElectricityContract.class);

    private ElectricityContract editingContract;

    private final List<ElectricityContract> contracts = new ArrayList<>();

    @Autowired
    public ElectricityContractsView(AuthService authService, I18nService i18n) {
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        nameField = new TextField(t("electricityContracts.field.name"));
        typeField = new ComboBox<>(t("electricityContracts.field.type"));

        basicFeeField = new NumberField(t("electricityContracts.field.basicFee"));
        nightPriceField = new NumberField(t("electricityContracts.field.nightPrice"));
        dayPriceField = new NumberField(t("electricityContracts.field.dayPrice"));
        staticPriceField = new NumberField(t("electricityContracts.field.staticPrice"));

        taxPercentField = new NumberField(t("electricityContracts.field.taxPercent"));
        taxAmountField = new NumberField(t("electricityContracts.field.taxAmount"));

        staticPricingToggle = new Checkbox(t("electricityContracts.field.staticToggle"));
        saveButton = new Button(t("electricityContracts.button.create"));

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("padding-top", "20px");

        H2 title = new H2(t("electricityContracts.title"));

        configureGrid();
        configureForm();

        VerticalLayout card = new VerticalLayout(title, grid, createFormLayout());
        card.setWidthFull();
        card.setMaxWidth("1400px");
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)")
                .set("border-radius", "12px")
                .set("padding", "32px")
                .set("background-color", "var(--lumo-base-color)");

        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification.show(t("electricityContracts.notification.sessionExpired"));
            UI.getCurrent().navigate(LoginView.class);
            return;
        }

        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();
    }

    private void configureGrid() {
        grid.addColumn(ElectricityContract::getName)
                .setHeader(t("electricityContracts.grid.name"))
                .setAutoWidth(true);

        grid.addColumn(contract -> t("electricityContracts.type." + contract.getType().name().toLowerCase()))
                .setHeader(t("electricityContracts.grid.type"));

        grid.addColumn(ElectricityContract::getBasicFee)
                .setHeader(t("electricityContracts.grid.basicFee"));

        grid.addColumn(ElectricityContract::getNightPrice)
                .setHeader(t("electricityContracts.grid.nightPrice"));

        grid.addColumn(ElectricityContract::getDayPrice)
                .setHeader(t("electricityContracts.grid.dayPrice"));

        grid.addColumn(ElectricityContract::getStaticPrice)
                .setHeader(t("electricityContracts.grid.staticPrice"));

        grid.addColumn(ElectricityContract::getTaxPercent)
                .setHeader(t("electricityContracts.grid.taxPercent"));

        grid.addColumn(ElectricityContract::getTaxAmount)
                .setHeader(t("electricityContracts.grid.taxAmount"));

        grid.setItems(contracts);
        grid.setHeight("300px");

        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                editContract(e.getValue());
            }
        });
    }

    private void configureForm() {
        typeField.setItems(ContractType.values());
        typeField.setItemLabelGenerator(type ->
                t("electricityContracts.type." + type.name().toLowerCase())
        );

        basicFeeField.setMin(0);
        nightPriceField.setMin(0);
        dayPriceField.setMin(0);
        staticPriceField.setMin(0);
        taxPercentField.setMin(0);
        taxAmountField.setMin(0);

        staticPricingToggle.addValueChangeListener(e -> togglePricingMode(e.getValue()));

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> save());

        binder.bind(nameField, ElectricityContract::getName, ElectricityContract::setName);
        binder.bind(typeField, ElectricityContract::getType, ElectricityContract::setType);
        binder.bind(basicFeeField, ElectricityContract::getBasicFee, ElectricityContract::setBasicFee);
        binder.bind(nightPriceField, ElectricityContract::getNightPrice, ElectricityContract::setNightPrice);
        binder.bind(dayPriceField, ElectricityContract::getDayPrice, ElectricityContract::setDayPrice);
        binder.bind(staticPriceField, ElectricityContract::getStaticPrice, ElectricityContract::setStaticPrice);
        binder.bind(taxPercentField, ElectricityContract::getTaxPercent, ElectricityContract::setTaxPercent);
        binder.bind(taxAmountField, ElectricityContract::getTaxAmount, ElectricityContract::setTaxAmount);
    }

    private Component createFormLayout() {
        VerticalLayout container = new VerticalLayout();
        container.setPadding(true);
        container.setSpacing(true);
        container.getStyle()
                .set("margin-top", "20px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        FormLayout form = new FormLayout();
        form.add(
                nameField,
                typeField,
                basicFeeField,
                staticPricingToggle,
                nightPriceField,
                dayPriceField,
                staticPriceField,
                taxPercentField,
                taxAmountField
        );

        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("900px", 3)
        );

        container.add(form, saveButton);
        return container;
    }

    private void togglePricingMode(boolean useStatic) {
        staticPriceField.setEnabled(useStatic);
        nightPriceField.setEnabled(!useStatic);
        dayPriceField.setEnabled(!useStatic);
    }

    private void save() {
        if (editingContract == null) {
            ElectricityContract contract = new ElectricityContract();
            if (binder.writeBeanIfValid(contract)) {
                contracts.add(contract);
                grid.getDataProvider().refreshAll();
                Notification.show(t("electricityContracts.notification.saved"));
                clearForm();
            }
        } else {
            if (binder.writeBeanIfValid(editingContract)) {
                grid.getDataProvider().refreshItem(editingContract);
                Notification.show(t("electricityContracts.notification.updated"));
                clearForm();
            }
        }
    }

    private void editContract(ElectricityContract contract) {
        this.editingContract = contract;
        binder.readBean(contract);
        saveButton.setText(t("electricityContracts.button.update"));
    }

    private void clearForm() {
        editingContract = null;
        binder.readBean(new ElectricityContract());
        saveButton.setText(t("electricityContracts.button.create"));
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

    public enum ContractType {
        ENERGY,
        TRANSFER
    }

    public static class ElectricityContract {
        private String name;
        private ContractType type;
        private Double basicFee;
        private Double nightPrice;
        private Double dayPrice;
        private Double staticPrice;
        private Double taxPercent;
        private Double taxAmount;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public ContractType getType() {
            return type;
        }

        public void setType(ContractType type) {
            this.type = type;
        }

        public Double getBasicFee() {
            return basicFee;
        }

        public void setBasicFee(Double basicFee) {
            this.basicFee = basicFee;
        }

        public Double getNightPrice() {
            return nightPrice;
        }

        public void setNightPrice(Double nightPrice) {
            this.nightPrice = nightPrice;
        }

        public Double getDayPrice() {
            return dayPrice;
        }

        public void setDayPrice(Double dayPrice) {
            this.dayPrice = dayPrice;
        }

        public Double getStaticPrice() {
            return staticPrice;
        }

        public void setStaticPrice(Double staticPrice) {
            this.staticPrice = staticPrice;
        }

        public Double getTaxPercent() {
            return taxPercent;
        }

        public void setTaxPercent(Double taxPercent) {
            this.taxPercent = taxPercent;
        }

        public Double getTaxAmount() {
            return taxAmount;
        }

        public void setTaxAmount(Double taxAmount) {
            this.taxAmount = taxAmount;
        }
    }
}
