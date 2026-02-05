package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.*;
import com.nitramite.porssiohjain.views.components.Divider;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import elemental.json.Json;
import elemental.json.JsonArray;
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

    private final Grid<ControlDeviceResponse> deviceGrid = new Grid<>(ControlDeviceResponse.class, false);
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
            SiteService siteService
    ) {
        this.authService = authService;
        this.controlService = controlService;
        this.deviceService = deviceService;
        this.controlSchedulerService = controlSchedulerService;
        this.nordpoolService = nordpoolService;
        this.i18n = i18n;
        this.contractRepository = contractRepository;
        this.siteService = siteService;

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
        }
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

    private void renderView() {
        removeAll();

        Long accountId = getAccountId();

        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        card.getStyle().set("border-radius", "12px");
        card.getStyle().set("padding", "32px");
        card.getStyle().set("background-color", "var(--lumo-base-color)");

        H2 title = new H2(t("controlTable.title", control.getName()));
        title.getStyle().set("margin-top", "0");
        card.add(title);

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

                Notification.show(t("controlTable.notification.saved"));
            } catch (Exception ex) {
                Notification.show(t("controlTable.notification.failedSave", ex.getMessage()));
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
        card.add(deviceGrid);
        card.add(createAddDeviceLayout());
        loadControlDevices();
        card.add(Divider.createDivider());
        card.add(getControlTableSection());

        add(card);
    }


    private void configureDeviceGrid() {
        deviceGrid.removeAllColumns();
        deviceGrid.addColumn(cd -> cd.getDevice().getDeviceName()).setHeader(t("controlTable.grid.deviceName"));
        deviceGrid.addColumn(ControlDeviceResponse::getDeviceChannel).setHeader(t("controlTable.grid.channel"));
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

    private void loadControlDevices() {
        deviceGrid.setItems(controlService.getControlDevices(controlId));
    }

    private Component createAddDeviceLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>(t("controlTable.deviceSelect"));
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        deviceSelect.setItems(deviceService.getAllDevicesForControlId(controlId));
        deviceSelect.setWidthFull();

        NumberField channelField = new NumberField(t("controlTable.field.channel"));
        channelField.setStep(1);
        channelField.setWidthFull();

        Button addButton = new Button(t("controlTable.button.addDevice"), e -> {
            if (deviceSelect.getValue() != null && channelField.getValue() != null) {
                controlService.addDeviceToControl(
                        getAccountId(),
                        controlId,
                        deviceSelect.getValue().getId(),
                        channelField.getValue().intValue()
                );
                loadControlDevices();
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
        List<Double> controlPrices = new ArrayList<>();
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

        Map<String, Double> controlMap = new HashMap<>();
        for (ControlTableResponse ctrl : controlTableResponses) {
            String ts = jsFormatter.format(ctrl.getStartTime());
            controlMap.put(ts, ctrl.getPriceSnt().doubleValue());
        }

        for (String ts : timestamps) {
            Double value = controlMap.get(ts);
            if (value == null) {
                controlPrices.add(Double.NaN);
            } else {
                controlPrices.add(value);
            }
        }

        if (transferContract != null) {
            BigDecimal nightPrice = transferContract.getNightPrice();
            BigDecimal dayPrice = transferContract.getDayPrice();
            BigDecimal taxAmount = transferContract.getTaxAmount();
            for (String ts : timestamps) {
                try {
                    LocalDateTime localDateTime = LocalDateTime.parse(ts, jsFormatter);
                    int hour = localDateTime.getHour();
                    boolean isNight = (hour >= 22 || hour < 7);
                    BigDecimal basePrice = isNight ? nightPrice : dayPrice;
                    if (basePrice == null) {
                        transferPrices.add(Double.NaN);
                    } else {
                        BigDecimal total = basePrice.add(taxAmount != null ? taxAmount : BigDecimal.ZERO);
                        transferPrices.add(total.doubleValue());
                    }
                } catch (Exception e) {
                    transferPrices.add(Double.NaN);
                }
            }
        }

        JsonArray jsTimestamps = Json.createArray();
        for (int i = 0; i < timestamps.size(); i++) {
            jsTimestamps.set(i, timestamps.get(i));
        }

        JsonArray jsNordpoolPrices = Json.createArray();
        for (int i = 0; i < nordpoolPrices.size(); i++) {
            jsNordpoolPrices.set(i, nordpoolPrices.get(i));
        }

        JsonArray jsControlPrices = Json.createArray();
        for (int i = 0; i < controlPrices.size(); i++) {
            Double val = controlPrices.get(i);
            if (val.isNaN()) {
                jsControlPrices.set(i, Json.createNull());
            } else {
                jsControlPrices.set(i, val);
            }
        }

        JsonArray jsTransferPrices = Json.createArray();
        for (int i = 0; i < transferPrices.size(); i++) {
            Double val = transferPrices.get(i);
            if (val.isNaN()) {
                jsTransferPrices.set(i, Json.createNull());
            } else {
                jsTransferPrices.set(i, val);
            }
        }

        String nordpoolLabel = t("controlTable.chart.nordpoolPrice");
        String controlLabel = t("controlTable.chart.controlPrice");
        String transferLabel = transferContract != null ? transferContract.getName() : t("controlTable.chart.noTransferContract");
        String xAxisLabel = t("controlTable.chart.time");
        String yAxisLabel = t("controlTable.chart.price");
        String chartTitle = t("controlTable.chart.title");
        String nowLabel = t("controlTable.chart.now");

        chartDiv.getElement().executeJs("""
                            const container = this;
                            const nordpoolLabel = $4;
                            const controlLabel = $5;
                            const transferLabel = $6;
                            const xAxisLabel = $7;
                            const yAxisLabel = $8;
                            const chartTitle = $9;
                            const nowLabel = $10;
                        
                            function renderOrUpdate(dataX, dataNordpool, dataControl, dataTransfer) {
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
                                            { name: nordpoolLabel, data: dataNordpool, color: '#0000FF' },
                                            { name: controlLabel, data: dataControl, color: '#FF0000' },
                                            { name: transferLabel, data: dataTransfer, color: '#FFB343' }
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
                                        { data: dataNordpool },
                                        { data: dataControl },
                                        { data: dataTransfer }
                                    ], true);
                                }
                            }
                        
                            renderOrUpdate($0, $1, $2, $3);
                        """,
                jsTimestamps, jsNordpoolPrices, jsControlPrices, jsTransferPrices,
                nordpoolLabel, controlLabel, transferLabel, xAxisLabel, yAxisLabel, chartTitle, nowLabel
        );
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
