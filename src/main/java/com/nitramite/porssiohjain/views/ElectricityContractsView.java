package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ContractType;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
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
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Locale;

@PageTitle("Pörssiohjain - Electricity Contracts")
@Route("electricity-contracts")
@PermitAll
public class ElectricityContractsView extends VerticalLayout implements BeforeEnterObserver {

    private final I18nService i18n;
    private final ElectricityContractRepository contractRepository;
    private final AccountRepository accountRepository;

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

    private final Grid<ElectricityContractEntity> grid = new Grid<>(ElectricityContractEntity.class, false);
    private final Binder<ElectricityContractEntity> binder = new Binder<>(ElectricityContractEntity.class);

    private ElectricityContractEntity editingContract;

    @Autowired
    public ElectricityContractsView(
            AuthService authService,
            I18nService i18n,
            ElectricityContractRepository contractRepository,
            AccountRepository accountRepository
    ) {
        this.i18n = i18n;
        this.contractRepository = contractRepository;
        this.accountRepository = accountRepository;

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
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        add(card);

        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            Notification notification = Notification.show(t("electricityContracts.notification.sessionExpired"));
            notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
            UI.getCurrent().navigate(LoginView.class);
            return;
        }

        AccountEntity account = authService.authenticate(token);
        accountId = account.getId();

        loadContracts();
    }

    private void configureGrid() {
        grid.addColumn(ElectricityContractEntity::getName)
                .setHeader(t("electricityContracts.grid.name"))
                .setAutoWidth(true);

        grid.addColumn(contract -> t("electricityContracts.type." + contract.getType().name().toLowerCase()))
                .setHeader(t("electricityContracts.grid.type"));

        grid.addColumn(ElectricityContractEntity::getBasicFee)
                .setHeader(t("electricityContracts.grid.basicFee"));

        grid.addColumn(ElectricityContractEntity::getNightPrice)
                .setHeader(t("electricityContracts.grid.nightPrice"));

        grid.addColumn(ElectricityContractEntity::getDayPrice)
                .setHeader(t("electricityContracts.grid.dayPrice"));

        grid.addColumn(ElectricityContractEntity::getStaticPrice)
                .setHeader(t("electricityContracts.grid.staticPrice"));

        grid.addColumn(ElectricityContractEntity::getTaxPercent)
                .setHeader(t("electricityContracts.grid.taxPercent"));

        grid.addColumn(ElectricityContractEntity::getTaxAmount)
                .setHeader(t("electricityContracts.grid.taxAmount"));

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

        binder.forField(nameField)
                .bind(ElectricityContractEntity::getName, ElectricityContractEntity::setName);

        binder.forField(typeField)
                .bind(ElectricityContractEntity::getType, ElectricityContractEntity::setType);

        binder.forField(basicFeeField)
                .withConverter(this::toBigDecimal, this::toDouble)
                .bind(ElectricityContractEntity::getBasicFee, ElectricityContractEntity::setBasicFee);

        binder.forField(nightPriceField)
                .withConverter(this::toBigDecimal, this::toDouble)
                .bind(ElectricityContractEntity::getNightPrice, ElectricityContractEntity::setNightPrice);

        binder.forField(dayPriceField)
                .withConverter(this::toBigDecimal, this::toDouble)
                .bind(ElectricityContractEntity::getDayPrice, ElectricityContractEntity::setDayPrice);

        binder.forField(staticPriceField)
                .withConverter(this::toBigDecimal, this::toDouble)
                .bind(ElectricityContractEntity::getStaticPrice, ElectricityContractEntity::setStaticPrice);

        binder.forField(taxPercentField)
                .withConverter(this::toBigDecimal, this::toDouble)
                .bind(ElectricityContractEntity::getTaxPercent, ElectricityContractEntity::setTaxPercent);

        binder.forField(taxAmountField)
                .withConverter(this::toBigDecimal, this::toDouble)
                .bind(ElectricityContractEntity::getTaxAmount, ElectricityContractEntity::setTaxAmount);
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
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated account not found: " + accountId
                ));

        if (editingContract == null) {
            ElectricityContractEntity contract = new ElectricityContractEntity();
            contract.setAccount(account);

            if (binder.writeBeanIfValid(contract)) {
                contractRepository.save(contract);
                loadContracts();
                Notification notification = Notification.show(t("electricityContracts.notification.saved"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                clearForm();
            }
        } else {
            if (binder.writeBeanIfValid(editingContract)) {
                editingContract.setAccount(account);
                contractRepository.save(editingContract);
                loadContracts();
                Notification notification = Notification.show(t("electricityContracts.notification.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                clearForm();
            }
        }
    }

    private void editContract(ElectricityContractEntity contract) {
        this.editingContract = contract;
        binder.readBean(contract);
        saveButton.setText(t("electricityContracts.button.update"));
    }

    private void clearForm() {
        editingContract = null;
        binder.readBean(new ElectricityContractEntity());
        saveButton.setText(t("electricityContracts.button.create"));
    }

    private void loadContracts() {
        grid.setItems(contractRepository.findByAccountId(accountId));
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
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
