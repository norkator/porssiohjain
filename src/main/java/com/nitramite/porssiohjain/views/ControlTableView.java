/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.*;
import com.nitramite.porssiohjain.views.components.Divider;
import com.nitramite.porssiohjain.views.components.InfoBox;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.nitramite.porssiohjain.views.components.Divider.createDivider;

@JsModule("./js/apexcharts.min.js")
@PageTitle("Pörssiohjain - Control table")
@Route("controls/:controlId")
@PermitAll
public class ControlTableView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final ControlService controlService;
    private final DeviceService deviceService;
    private final ControlSchedulerService controlSchedulerService;
    private final NordpoolService nordpoolService;
    protected final I18nService i18n;
    private final ElectricityContractRepository contractRepository;
    private final SiteService siteService;
    private final HeatPumpStateDialogService heatPumpStateDialogService;

    private final Grid<ControlDeviceResponse> deviceGrid = new Grid<>(ControlDeviceResponse.class, false);
    private final Grid<ControlHeatPumpResponse> heatPumpGrid = new Grid<>(ControlHeatPumpResponse.class, false);
    private final Grid<ControlTableResponse> controlTableGrid = new Grid<>(ControlTableResponse.class, false);

    private Long controlId;
    private ControlResponse control;
    private ElectricityContractEntity transferContract;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private Div chartTodayDiv, chartTomorrowDiv;

    private final Instant dateNow = Instant.now();
    private final Instant startOfDay = dateNow.truncatedTo(ChronoUnit.DAYS);
    private final Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS).minusNanos(1);
    private final Instant startOfTomorrow = dateNow.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
    private final Instant endOfDayTomorrow = startOfTomorrow.plus(1, ChronoUnit.DAYS).minusNanos(1);


    @Autowired
    public ControlTableView(
            AuthService authService,
            ControlService controlService,
            DeviceService deviceService,
            ControlSchedulerService controlSchedulerService,
            NordpoolService nordpoolService,
            I18nService i18n,
            ElectricityContractRepository contractRepository,
            SiteService siteService,
            HeatPumpStateDialogService heatPumpStateDialogService
    ) {
        this.authService = authService;
        this.controlService = controlService;
        this.deviceService = deviceService;
        this.controlSchedulerService = controlSchedulerService;
        this.nordpoolService = nordpoolService;
        this.i18n = i18n;
        this.contractRepository = contractRepository;
        this.siteService = siteService;
        this.heatPumpStateDialogService = heatPumpStateDialogService;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        setSpacing(true);
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, authService)) {
            return;
        }

        try {
            controlId = Long.valueOf(event.getRouteParameters().get("controlId").orElseThrow());
            loadControl();
            renderView();
        } catch (Exception e) {
            add(new Paragraph(t("controlTable.errorLoad", e.getMessage())));
        }
    }

    private void loadControl() {
        this.control = controlService.getControl(getAccountId(), controlId);
        loadTransferContract();
    }

    private void loadTransferContract() {
        if (control.getTransferContractId() != null) {
            Optional<ElectricityContractEntity> contract = contractRepository.findById(control.getTransferContractId());
            contract.ifPresent(electricityContractEntity -> this.transferContract = electricityContractEntity);
        } else {
            this.transferContract = null;
        }
    }

    private Long getAccountId() {
        AccountEntity account = ViewAuthUtils.getAuthenticatedAccount(authService, t("controlTable.sessionExpired"));
        if (account == null) {
            return null;
        }
        return account.getId();
    }

    private void renderView() {
        removeAll();

        Long accountId = getAccountId();
        if (accountId == null) {
            return;
        }

        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 title = new H2(t("controlTable.title", control.getName()));
        title.getStyle().set("margin-top", "0");
        card.add(title);

        card.add(new InfoBox(t("common.hint"), t("controlTable.transferContractHint")));

        TextField controlNameField = new TextField(t("controlTable.field.name"));
        controlNameField.setValue(control.getName());

        NumberField maxPriceField = new NumberField(t("controlTable.field.maxPrice"));
        maxPriceField.setValue(control.getMaxPriceSnt().doubleValue());

        NumberField minPriceField = new NumberField(t("controlTable.field.minPrice"));
        minPriceField.setValue(control.getMinPriceSnt().doubleValue());

        NumberField dailyMinutes = new NumberField(t("controlTable.field.dailyMinutes"));
        dailyMinutes.setValue(control.getDailyOnMinutes().doubleValue());

        NumberField taxPercentage = new NumberField(t("controlTable.field.taxPercent"));
        taxPercentage.setValue(control.getTaxPercent().doubleValue());

        ComboBox<ControlMode> modeCombo = new ComboBox<>(t("controlTable.field.mode"));
        modeCombo.setItems(ControlMode.values());
        modeCombo.setValue(control.getMode());
        modeCombo.setWidthFull();

        Checkbox manualToggle = new Checkbox(t("controlTable.field.manualOn"));
        manualToggle.getStyle().set("margin-top", "12px");
        manualToggle.setValue(control.getManualOn());

        Checkbox alwaysOnBelowMinPriceToggle = new Checkbox(t("controlTable.field.alwaysOnBelowMinPrice"));
        alwaysOnBelowMinPriceToggle.getStyle().set("margin-top", "12px");
        alwaysOnBelowMinPriceToggle.setValue(control.getAlwaysOnBelowMinPrice());

        List<ElectricityContractEntity> contracts =
                contractRepository.findByAccountId(getAccountId());

        ComboBox<ElectricityContractEntity> energyContractCombo =
                new ComboBox<>(t("controlTable.field.energyContract"));

        ComboBox<ElectricityContractEntity> transferContractCombo =
                new ComboBox<>(t("controlTable.field.transferContract"));

        energyContractCombo.setItems(
                contracts.stream()
                        .filter(c -> c.getType() == ContractType.ENERGY)
                        .toList()
        );

        transferContractCombo.setItems(
                contracts.stream()
                        .filter(c -> c.getType() == ContractType.TRANSFER)
                        .toList()
        );

        if (control.getEnergyContractId() != null) {
            contracts.stream()
                    .filter(c -> c.getId().equals(control.getEnergyContractId()))
                    .findFirst()
                    .ifPresent(energyContractCombo::setValue);
        }

        if (control.getTransferContractId() != null) {
            contracts.stream()
                    .filter(c -> c.getId().equals(control.getTransferContractId()))
                    .findFirst()
                    .ifPresent(transferContractCombo::setValue);
        }

        energyContractCombo.setItemLabelGenerator(ElectricityContractEntity::getName);
        transferContractCombo.setItemLabelGenerator(ElectricityContractEntity::getName);

        energyContractCombo.setClearButtonVisible(true);
        transferContractCombo.setClearButtonVisible(true);
        energyContractCombo.setWidthFull();
        transferContractCombo.setWidthFull();

        List<SiteResponse> sites = siteService.getAllSites(accountId);
        ComboBox<SiteResponse> siteBox = new ComboBox<>(t("controlTable.field.site"));
        siteBox.setItems(sites);
        siteBox.setItemLabelGenerator(SiteResponse::getName);
        siteBox.setClearButtonVisible(true);
        sites.stream()
                .filter(s -> s.getId().equals(control.getSiteId()))
                .findFirst()
                .ifPresent(siteBox::setValue);

        Button saveButton = new Button(t("controlTable.button.save"), e -> {
            try {
                control.setName(controlNameField.getValue());
                control.setMaxPriceSnt(BigDecimal.valueOf(maxPriceField.getValue()));
                control.setMinPriceSnt(BigDecimal.valueOf(minPriceField.getValue()));
                control.setDailyOnMinutes(dailyMinutes.getValue().intValue());
                control.setTaxPercent(BigDecimal.valueOf(taxPercentage.getValue()));
                control.setMode(modeCombo.getValue());
                control.setAlwaysOnBelowMinPrice(alwaysOnBelowMinPriceToggle.getValue());
                if (control.getMode() == ControlMode.MANUAL) {
                    control.setManualOn(manualToggle.getValue());
                }
                ElectricityContractEntity energy = energyContractCombo.getValue();
                ElectricityContractEntity transfer = transferContractCombo.getValue();
                Long energyId = energy != null ? energy.getId() : null;
                Long transferId = transfer != null ? transfer.getId() : null;

                SiteResponse site = siteBox.getValue();
                Long siteId = site != null ? site.getId() : null;

                controlService.updateControl(
                        accountId,
                        controlId,
                        control.getName(),
                        control.getMaxPriceSnt(),
                        control.getMinPriceSnt(),
                        control.getDailyOnMinutes(),
                        control.getTaxPercent(),
                        control.getMode(),
                        control.getManualOn(),
                        control.getAlwaysOnBelowMinPrice(),
                        energyId,
                        transferId,
                        siteId
                );

                Notification notification = Notification.show(t("controlTable.notification.saved"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                loadControl();
            } catch (Exception ex) {
                Notification notification = Notification.show(t("controlTable.notification.failedSave", ex.getMessage()));
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout formLayout = new FormLayout();
        formLayout.setWidthFull();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("400px", 3)
        );

        formLayout.add(
                controlNameField,
                modeCombo,
                maxPriceField,
                minPriceField,
                taxPercentage,
                dailyMinutes,
                energyContractCombo,
                transferContractCombo,
                siteBox,
                manualToggle,
                alwaysOnBelowMinPriceToggle
        );

        card.add(formLayout, saveButton);

        Runnable updateFieldStates = () -> {
            ControlMode mode = modeCombo.getValue();

            boolean isBelowMax = mode == ControlMode.BELOW_MAX_PRICE;
            boolean isCheapest = mode == ControlMode.CHEAPEST_HOURS;
            boolean isManual = mode == ControlMode.MANUAL;

            maxPriceField.setEnabled(!isManual);
            dailyMinutes.setEnabled(isCheapest);
            taxPercentage.setEnabled(true);
            manualToggle.setEnabled(isManual);
        };

        modeCombo.addValueChangeListener(e -> updateFieldStates.run());
        updateFieldStates.run();

        card.add(new H3(t("controlTable.section.devices")));
        configureDeviceGrid();
        configureHeatPumpGrid();

        Tab standardTab = new Tab(t("device.type.standard"));
        Tab heatPumpTab = new Tab(t("device.type.heatPump"));
        Tabs deviceTabs = new Tabs(standardTab, heatPumpTab);

        VerticalLayout standardLayout = new VerticalLayout(deviceGrid, createAddDeviceLayout());
        standardLayout.setPadding(false);
        standardLayout.setSpacing(true);

        VerticalLayout heatPumpLayout = new VerticalLayout(heatPumpGrid, createAddHeatPumpLayout());
        heatPumpLayout.setPadding(false);
        heatPumpLayout.setSpacing(true);
        heatPumpLayout.setVisible(false);

        Map<Tab, Component> tabsToPages = new HashMap<>();
        tabsToPages.put(standardTab, standardLayout);
        tabsToPages.put(heatPumpTab, heatPumpLayout);

        Div pages = new Div(standardLayout, heatPumpLayout);
        pages.setWidthFull();

        deviceTabs.addSelectedChangeListener(event -> {
            tabsToPages.values().forEach(page -> page.setVisible(false));
            Component selectedPage = tabsToPages.get(deviceTabs.getSelectedTab());
            selectedPage.setVisible(true);
        });

        card.add(deviceTabs, pages);

        loadControlDevices();
        loadControlHeatPumps();
        card.add(Divider.createDivider());
        card.add(getControlTableSection());
        card.add(Divider.createDivider());

        Button deleteButton = new Button(t("button.delete"), e -> {
            deleteResourceDialog();
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        card.add(deleteButton);

        add(card);
    }


    private void configureDeviceGrid() {
        deviceGrid.removeAllColumns();
        deviceGrid.addColumn(cd -> cd.getDevice().getDeviceName()).setHeader(t("controlTable.grid.deviceName"));
        deviceGrid.addColumn(ControlDeviceResponse::getDeviceChannel).setHeader(t("controlTable.grid.channel"));
        deviceGrid.addColumn(ControlDeviceResponse::getEstimatedPowerKw).setHeader(t("controlTable.grid.estimatedPowerKw"));
        deviceGrid.addColumn(cd -> cd.getDevice().getUuid()).setHeader(t("controlTable.grid.uuid"));
        deviceGrid.addComponentColumn(cd -> {
            Button delete = new Button(t("controlTable.button.delete"), e -> {
                controlService.deleteControlDevice(getAccountId(), cd.getId());
                loadControlDevices();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return delete;
        }).setHeader(t("controlTable.grid.actions"));
        deviceGrid.setMaxHeight("200px");
    }

    private void configureHeatPumpGrid() {
        heatPumpGrid.removeAllColumns();
        heatPumpGrid.addColumn(cd -> cd.getDevice().getDeviceName()).setHeader(t("controlTable.grid.deviceName"));
        heatPumpGrid.addColumn(cd -> t("controlAction." + cd.getControlAction().name())).setHeader(t("controlTable.grid.action"));
        heatPumpGrid.addColumn(cd -> cd.getComparisonType() != null ? t("comparisonType." + cd.getComparisonType().name()) : "").setHeader(t("controlTable.grid.comparisonType"));
        heatPumpGrid.addColumn(ControlHeatPumpResponse::getPriceLimit).setHeader(t("controlTable.grid.priceLimit"));
        heatPumpGrid.addColumn(ControlHeatPumpResponse::getEstimatedPowerKw).setHeader(t("controlTable.grid.estimatedPowerKw"));
        heatPumpGrid.addColumn(ControlHeatPumpResponse::getStateHex).setHeader(t("controlTable.grid.stateHex"));
        heatPumpGrid.addComponentColumn(cd -> {
            Button decode = new Button(t("controlTable.button.decodeState"), e -> openHeatPumpStateHexDialog(cd.getStateHex()));
            Button delete = new Button(t("controlTable.button.delete"), e -> {
                controlService.deleteControlHeatPump(getAccountId(), cd.getId());
                loadControlHeatPumps();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            HorizontalLayout actions = new HorizontalLayout(decode, delete);
            actions.setPadding(false);
            actions.setSpacing(true);
            actions.setMargin(false);
            return actions;
        }).setHeader(t("controlTable.grid.actions"));
        heatPumpGrid.setMaxHeight("200px");
    }

    private void loadControlDevices() {
        deviceGrid.setItems(
                controlService.getControlDevices(getAccountId(), controlId).stream()
                        .filter(cd -> cd.getDevice().getDeviceType() == DeviceType.STANDARD)
                        .toList()
        );
    }

    private void loadControlHeatPumps() {
        heatPumpGrid.setItems(
                controlService.getControlHeatPumps(getAccountId(), controlId).stream()
                        .filter(cd -> cd.getDevice().getDeviceType() == DeviceType.HEAT_PUMP)
                        .toList()
        );
    }

    private Component createAddDeviceLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>(t("controlTable.deviceSelect"));
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        deviceSelect.setItems(
                deviceService.getAllDevicesForControlId(getAccountId(), controlId).stream()
                        .filter(d -> d.getDeviceType() == DeviceType.STANDARD)
                        .toList()
        );
        deviceSelect.setWidthFull();

        NumberField channelField = new NumberField(t("controlTable.field.channel"));
        channelField.setStep(1);
        channelField.setWidthFull();

        NumberField estimatedPowerKwField = new NumberField(t("controlTable.field.estimatedPowerKw"));
        estimatedPowerKwField.setStep(0.1);
        estimatedPowerKwField.setMin(0);
        estimatedPowerKwField.setWidthFull();

        Button addButton = new Button(t("controlTable.button.addDevice"), e -> {
            if (deviceSelect.getValue() != null && channelField.getValue() != null) {
                controlService.addDeviceToControl(
                        getAccountId(),
                        controlId,
                        deviceSelect.getValue().getId(),
                        channelField.getValue().intValue(),
                        estimatedPowerKwField.getValue() != null ? BigDecimal.valueOf(estimatedPowerKwField.getValue()) : null
                );
                loadControlDevices();
            }
        });
        addButton.setWidthFull();

        FormLayout formLayout = new FormLayout(deviceSelect, channelField, estimatedPowerKwField, addButton);
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

    private Component createAddHeatPumpLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>(t("controlTable.deviceSelect"));
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        deviceSelect.setItems(
                deviceService.getAllDevicesForControlId(getAccountId(), controlId).stream()
                        .filter(d -> d.getDeviceType() == DeviceType.HEAT_PUMP)
                        .toList()
        );
        deviceSelect.setWidthFull();

        TextField stateHexField = new TextField(t("controlTable.field.stateHex"));
        stateHexField.setReadOnly(true);
        stateHexField.setWidthFull();

        Button queryStateButton = new Button(t("controlTable.button.queryState"), e -> {
            if (deviceSelect.getValue() != null) {
                openHeatPumpStateDialog(deviceSelect.getValue(), stateHexField);
            } else {
                Notification.show(t("controlTable.notification.selectDeviceFirst"), 3000, Notification.Position.MIDDLE);
            }
        });
        queryStateButton.setWidthFull();

        ComboBox<ControlAction> actionCombo = new ComboBox<>(t("controlTable.field.action"));
        actionCombo.setItems(ControlAction.values());
        actionCombo.setItemLabelGenerator(action -> t("controlAction." + action.name()));
        actionCombo.setWidthFull();

        ComboBox<ComparisonType> comparisonCombo = new ComboBox<>(t("controlTable.field.comparisonType"));
        comparisonCombo.setItems(ComparisonType.values());
        comparisonCombo.setItemLabelGenerator(type -> t("comparisonType." + type.name()));
        comparisonCombo.setWidthFull();

        NumberField priceLimitField = new NumberField(t("controlTable.field.priceLimit"));
        priceLimitField.setStep(0.1);
        priceLimitField.setWidthFull();

        NumberField estimatedPowerKwField = new NumberField(t("controlTable.field.estimatedPowerKw"));
        estimatedPowerKwField.setStep(0.1);
        estimatedPowerKwField.setMin(0);
        estimatedPowerKwField.setWidthFull();

        Button addButton = new Button(t("controlTable.button.addDevice"), e -> {
            if (deviceSelect.getValue() != null && !stateHexField.getValue().isEmpty() && actionCombo.getValue() != null) {
                controlService.addHeatPumpToControl(
                        getAccountId(),
                        controlId,
                        deviceSelect.getValue().getId(),
                        stateHexField.getValue(),
                        actionCombo.getValue(),
                        comparisonCombo.getValue(),
                        priceLimitField.getValue() != null ? BigDecimal.valueOf(priceLimitField.getValue()) : null,
                        estimatedPowerKwField.getValue() != null ? BigDecimal.valueOf(estimatedPowerKwField.getValue()) : null
                );
                loadControlHeatPumps();
                stateHexField.clear();
            }
        });
        addButton.setWidthFull();

        FormLayout formLayout = new FormLayout(deviceSelect, queryStateButton, stateHexField, actionCombo, comparisonCombo, priceLimitField, estimatedPowerKwField, addButton);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 4)
        );

        formLayout.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.1)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        return formLayout;
    }

    private void openHeatPumpStateDialog(DeviceResponse deviceResponse, TextField stateHexField) {
        heatPumpStateDialogService.openStateDialog(deviceResponse, stateHexField);
    }

    private void openHeatPumpStateHexDialog(String stateHex) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("controlTable.dialog.decodeState.title"));
        dialog.setWidth("900px");
        dialog.setMaxWidth("95vw");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setWidthFull();
        String instructionsKey = heatPumpStateDialogService.isJsonState(stateHex)
                ? "controlTable.dialog.decodeState.jsonInstructions"
                : "controlTable.dialog.decodeState.instructions";
        dialogLayout.add(
                new Paragraph(t(instructionsKey)),
                heatPumpStateDialogService.createAcStateInfoContentFromHex(stateHex)
        );

        dialog.add(dialogLayout);
        dialog.getFooter().add(new Button(t("common.cancel"), e -> dialog.close()));
        dialog.open();
    }


    private VerticalLayout getControlTableSection() {
        controlTableGrid.removeAllColumns();
        controlTableGrid.addColumn(entry -> {
            ZoneId zone = ZoneId.systemDefault();
            try {
                if (control.getTimezone() != null) {
                    zone = ZoneId.of(control.getTimezone());
                }
            } catch (Exception ignored) {
            }
            return ZonedDateTime.ofInstant(entry.getStartTime(), zone).format(formatter);
        }).setHeader(t("controlTable.grid.startTime"));
        controlTableGrid.addColumn(entry -> {
            ZoneId zone = ZoneId.systemDefault();
            try {
                if (control.getTimezone() != null) {
                    zone = ZoneId.of(control.getTimezone());
                }
            } catch (Exception ignored) {
            }
            return ZonedDateTime.ofInstant(entry.getEndTime(), zone).format(formatter);
        }).setHeader(t("controlTable.grid.endTime"));
        controlTableGrid.addColumn(ControlTableResponse::getPriceSnt).setHeader(t("controlTable.grid.price"));
        controlTableGrid.addColumn(ControlTableResponse::getStatus).setHeader(t("controlTable.grid.status"));
        controlTableGrid.setAllRowsVisible(true);

        Button recalcButton = new Button(t("controlTable.button.recalculate"), e -> {
            controlSchedulerService.generateForControl(controlId);
            List<ControlTableResponse> controlTableResponses = controlSchedulerService.findByControlId(controlId);
            List<NordpoolPriceResponse> nordpoolPriceResponsesToday = nordpoolService.getNordpoolPricesForControl(
                    controlId, startOfDay, endOfDay
            );
            List<NordpoolPriceResponse> nordpoolPriceResponsesTomorrow = nordpoolService.getNordpoolPricesForControl(
                    controlId, startOfTomorrow, endOfDayTomorrow
            );
            refreshControlTable();
            controlTableGrid.setItems(controlTableResponses);
            updatePriceChart(chartTodayDiv, controlTableResponses, nordpoolPriceResponsesToday, this.control.getTimezone(), transferContract);
            updatePriceChart(chartTomorrowDiv, controlTableResponses, nordpoolPriceResponsesTomorrow, this.control.getTimezone(), transferContract);
        });
        recalcButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);


        List<ControlTableResponse> controlTableResponses = controlSchedulerService.findByControlId(controlId);
        List<NordpoolPriceResponse> nordpoolPriceResponsesToday = nordpoolService.getNordpoolPricesForControl(
                controlId, startOfDay, endOfDay
        );
        List<NordpoolPriceResponse> nordpoolPriceResponsesTomorrow = nordpoolService.getNordpoolPricesForControl(
                controlId, startOfTomorrow, endOfDayTomorrow
        );

        refreshControlTable();

        VerticalLayout layout = new VerticalLayout(
                new HorizontalLayout(
                        new H3(t("controlTable.section.title")),
                        recalcButton
                ),
                new Div(new Text(t("controlTable.section.descriptionCharts"))),
                createPriceCharts(controlTableResponses, nordpoolPriceResponsesToday, nordpoolPriceResponsesTomorrow),
                new InfoBox(t("common.hint"), t("controlTable.chartHint")),
                createDivider(),
                new Div(new Text(t("controlTable.section.descriptionList"))),
                controlTableGrid
        );
        layout.setWidthFull();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.getStyle().set("margin", "0");

        layout.getChildren()
                .filter(c -> c instanceof HorizontalLayout)
                .map(c -> (HorizontalLayout) c)
                .forEach(h -> {
                    h.setPadding(false);
                    h.setSpacing(true);
                    h.getStyle().set("margin", "0");
                });

        return layout;
    }


    private void refreshControlTable() {
        List<ControlTableResponse> list = controlSchedulerService.findByControlId(controlId);
        controlTableGrid.setItems(list);
    }

    private void deleteResourceDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("delete.confirmTitle"));
        dialog.add(t("delete.confirmDescription"));
        Button deleteButton = new Button(t("button.delete"), (e) -> {
            controlService.deleteControl(getAccountId(), controlId);
            dialog.close();
            UI.getCurrent().navigate(ControlsView.class);
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        deleteButton.getStyle().set("margin-right", "auto");
        dialog.getFooter().add(deleteButton);
        Button cancelButton = new Button(t("button.cancel"), (e) -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton);
        dialog.open();
    }

    private Div createPriceCharts(
            List<ControlTableResponse> controlTableResponses,
            List<NordpoolPriceResponse> nordpoolPriceResponses,
            List<NordpoolPriceResponse> nordpoolPriceResponsesTomorrow
    ) {
        chartTodayDiv = new Div();
        chartTodayDiv.setId("prices-today-chart");
        chartTodayDiv.setWidthFull();
        chartTodayDiv.setHeight("400px");
        updatePriceChart(chartTodayDiv, controlTableResponses, nordpoolPriceResponses, this.control.getTimezone(), transferContract);

        chartTomorrowDiv = new Div();
        chartTomorrowDiv.setId("prices-tomorrow-chart");
        chartTomorrowDiv.setWidthFull();
        if (!nordpoolPriceResponsesTomorrow.isEmpty()) {
            chartTomorrowDiv.setHeight("250px");
            updatePriceChart(chartTomorrowDiv, controlTableResponses, nordpoolPriceResponsesTomorrow, this.control.getTimezone(), transferContract);
        }

        Div chartsDiv = new Div();
        chartsDiv.setId("charts-div");
        chartsDiv.setWidthFull();
        chartsDiv.add(chartTodayDiv, chartTomorrowDiv);
        return chartsDiv;
    }

    private void updatePriceChart(
            Div chartDiv,
            List<ControlTableResponse> controlTableResponses,
            List<NordpoolPriceResponse> nordpoolPriceResponses,
            String timezone,
            ElectricityContractEntity transferContract
    ) {
        List<String> timestamps = new ArrayList<>();
        List<Double> nordpoolPrices = new ArrayList<>();
        List<Double> finalControlPrices = new ArrayList<>();
        List<Double> plannedControlPrices = new ArrayList<>();
        List<Double> transferPrices = new ArrayList<>();

        ZoneId zone = ZoneId.systemDefault();
        try {
            if (timezone != null) {
                zone = ZoneId.of(timezone);
            }
        } catch (Exception ignored) {
        }

        DateTimeFormatter jsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(zone);

        for (NordpoolPriceResponse entry : nordpoolPriceResponses) {
            String ts = jsFormatter.format(entry.getDeliveryStart());
            timestamps.add(ts);
            nordpoolPrices.add(entry.getPriceFiWithTax().doubleValue());
        }

        Map<String, ControlTableResponse> controlMap = new HashMap<>();
        for (ControlTableResponse ctrl : controlTableResponses) {
            String ts = jsFormatter.format(ctrl.getStartTime());
            controlMap.put(ts, ctrl);
        }

        for (String ts : timestamps) {
            ControlTableResponse ctrl = controlMap.get(ts);
            if (ctrl != null) {
                if (ctrl.getStatus() == Status.FINAL) {
                    finalControlPrices.add(ctrl.getPriceSnt().doubleValue());
                    plannedControlPrices.add(null);
                } else if (ctrl.getStatus() == Status.PLANNED) {
                    finalControlPrices.add(null);
                    plannedControlPrices.add(ctrl.getPriceSnt().doubleValue());
                } else {
                    finalControlPrices.add(null);
                    plannedControlPrices.add(null);
                }
            } else {
                finalControlPrices.add(null);
                plannedControlPrices.add(null);
            }
        }

        if (transferContract != null) {
            BigDecimal staticPrice = transferContract.getStaticPrice();
            BigDecimal nightPrice = transferContract.getNightPrice();
            BigDecimal dayPrice = transferContract.getDayPrice();
            BigDecimal taxAmount = transferContract.getTaxAmount();
            BigDecimal tax = taxAmount != null ? taxAmount : BigDecimal.ZERO;
            boolean hasStatic = staticPrice != null;
            boolean hasDayNight = dayPrice != null || nightPrice != null;
            for (String ts : timestamps) {
                try {
                    BigDecimal basePrice = null;
                    if (hasStatic && !hasDayNight) {
                        basePrice = staticPrice;
                    } else if (hasDayNight) {
                        LocalDateTime localDateTime = LocalDateTime.parse(ts, jsFormatter);
                        int hour = localDateTime.getHour();
                        boolean isNight = (hour >= 22 || hour < 7);
                        basePrice = isNight ? nightPrice : dayPrice;
                    }
                    if (basePrice == null) {
                        transferPrices.add(Double.NaN);
                    } else {
                        transferPrices.add(basePrice.add(tax).doubleValue());
                    }
                } catch (Exception e) {
                    transferPrices.add(Double.NaN);
                }
            }
        }

        String nordpoolLabel = t("controlTable.chart.nordpoolPrice");
        String finalControlLabel = t("controlTable.chart.controlPriceFinal");
        String plannedControlLabel = t("controlTable.chart.controlPricePlanned");
        String transferLabel = transferContract != null ? transferContract.getName() : t("controlTable.chart.noTransferContract");
        String xAxisLabel = t("controlTable.chart.time");
        String yAxisLabel = t("controlTable.chart.price");
        String chartTitle = t("controlTable.chart.title");
        String nowLabel = t("controlTable.chart.now");

        chartDiv.getElement().executeJs("""
                            const container = this;
                            const nordpoolLabel = $5;
                            const finalControlLabel = $6;
                            const plannedControlLabel = $7;
                            const transferLabel = $8;
                            const xAxisLabel = $9;
                            const yAxisLabel = $10;
                            const chartTitle = $11;
                            const nowLabel = $12;
                        
                            function renderOrUpdate(dataX, dataNordpool, dataFinalControl, dataPlannedControl, dataTransfer) {
                                const now = new Date();
                                const nowISO = now.toISOString().slice(0, 16);
                                const closest = dataX.reduce((prev, curr) => {
                                    return Math.abs(new Date(curr) - now) < Math.abs(new Date(prev) - now) ? curr : prev;
                                });
                        
                                if (!container.chartInstance) {
                                    const options = {
                                        chart: {
                                            type: 'line',
                                            height: '400px',
                                            toolbar: { show: true },
                                            zoom: { enabled: false }
                                        },
                                        series: [
                                            { name: transferLabel, data: dataTransfer, color: '#FFB343' },
                                            { name: nordpoolLabel, data: dataNordpool, color: '#0000FF' },
                                            { name: finalControlLabel, data: dataFinalControl, color: '#FF0000' },
                                            { name: plannedControlLabel, data: dataPlannedControl, color: '#800080' },
                                        ],
                                        xaxis: { categories: dataX, title: { text: xAxisLabel }, labels: { rotate: -45 } },
                                        yaxis: { title: { text: yAxisLabel } },
                                        title: { text: chartTitle, align: 'center' },
                                        stroke: { curve: 'smooth', width: 2 },
                                        markers: { size: 4 },
                                        tooltip: { shared: true },
                                        annotations: {
                                            xaxis: [
                                                {
                                                    x: closest,
                                                    borderColor: '#00E396',
                                                    label: {
                                                        style: { color: '#fff', background: '#00E396' },
                                                        text: nowLabel
                                                    }
                                                }
                                            ]
                                        }
                                    };
                                    container.chartInstance = new ApexCharts(container, options);
                                    container.chartInstance.render();
                                } else {
                                    container.chartInstance.updateOptions({
                                        xaxis: { categories: dataX },
                                        annotations: {
                                            xaxis: [
                                                {
                                                    x: closest,
                                                    borderColor: '#00E396',
                                                    label: {
                                                        style: { color: '#fff', background: '#00E396' },
                                                        text: nowLabel
                                                    }
                                                }
                                            ]
                                        }
                                    });
                                    container.chartInstance.updateSeries([
                                        { data: dataTransfer },
                                        { data: dataNordpool },
                                        { data: dataFinalControl },
                                        { data: dataPlannedControl },
                                    ], true);
                                }
                            }
                        
                            renderOrUpdate($0, $1, $2, $3, $4);
                        """,
                timestamps, nordpoolPrices, finalControlPrices, plannedControlPrices, transferPrices,
                nordpoolLabel, finalControlLabel, plannedControlLabel, transferLabel, xAxisLabel, yAxisLabel, chartTitle, nowLabel
        );
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
