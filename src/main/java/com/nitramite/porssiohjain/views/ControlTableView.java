package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlMode;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import elemental.json.Json;
import elemental.json.JsonArray;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Route("controls/:controlId")
@PermitAll
public class ControlTableView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final ControlService controlService;
    private final DeviceService deviceService;
    private final ControlSchedulerService controlSchedulerService;
    private final NordpoolService nordpoolService;
    protected final I18nService i18n;

    private final Grid<ControlDeviceResponse> deviceGrid = new Grid<>(ControlDeviceResponse.class, false);
    private final Grid<ControlTableResponse> controlTableGrid = new Grid<>(ControlTableResponse.class, false);

    private Long controlId;
    private ControlResponse control;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private Div chartTodayDiv, chartTomorrowDiv;

    private final Instant dateNow = Instant.now();
    private Instant startOfDay = dateNow.truncatedTo(ChronoUnit.DAYS);
    private Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS).minusNanos(1);
    private Instant startOfTomorrow = dateNow.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
    private Instant endOfDayTomorrow = startOfTomorrow.plus(1, ChronoUnit.DAYS).minusNanos(1);


    @Autowired
    public ControlTableView(
            AuthService authService,
            ControlService controlService,
            DeviceService deviceService,
            ControlSchedulerService controlSchedulerService,
            NordpoolService nordpoolService,
            I18nService i18n
    ) {
        this.authService = authService;
        this.controlService = controlService;
        this.deviceService = deviceService;
        this.controlSchedulerService = controlSchedulerService;
        this.nordpoolService = nordpoolService;
        this.i18n = i18n;

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
        this.control = controlService.getControl(controlId);
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

        add(new H2(t("controlTable.title", control.getName())));

        TextField controlNameField = new TextField(t("controlTable.field.name"));
        controlNameField.setValue(control.getName());

        NumberField maxPriceField = new NumberField(t("controlTable.field.maxPrice"));
        maxPriceField.setValue(control.getMaxPriceSnt().doubleValue());

        NumberField dailyMinutes = new NumberField(t("controlTable.field.dailyMinutes"));
        dailyMinutes.setValue(control.getDailyOnMinutes().doubleValue());

        NumberField taxPercentage = new NumberField(t("controlTable.field.taxPercent"));
        taxPercentage.setValue(control.getTaxPercent().doubleValue());

        ComboBox<ControlMode> modeCombo = new ComboBox<>(t("controlTable.field.mode"));
        modeCombo.setItems(ControlMode.values());
        modeCombo.setValue(control.getMode());
        modeCombo.setWidthFull();

        Checkbox manualToggle = new Checkbox(t("controlTable.field.manualOn"));
        manualToggle.setValue(control.getManualOn());

        Button saveButton = new Button(t("controlTable.button.save"), e -> {
            try {
                control.setName(controlNameField.getValue());
                control.setMaxPriceSnt(BigDecimal.valueOf(maxPriceField.getValue()));
                control.setDailyOnMinutes(dailyMinutes.getValue().intValue());
                control.setTaxPercent(BigDecimal.valueOf(taxPercentage.getValue()));
                control.setMode(modeCombo.getValue());
                if (control.getMode() == ControlMode.MANUAL) {
                    control.setManualOn(manualToggle.getValue());
                }

                controlService.updateControl(
                        getAccountId(),
                        controlId,
                        control.getName(),
                        control.getMaxPriceSnt(),
                        control.getDailyOnMinutes(),
                        control.getTaxPercent(),
                        control.getMode(),
                        control.getManualOn()
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
                taxPercentage,
                dailyMinutes,
                manualToggle
        );

        add(formLayout, saveButton);

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

        add(new H3(t("controlTable.section.devices")));
        configureDeviceGrid();
        add(deviceGrid);

        add(createAddDeviceLayout());
        loadControlDevices();

        add(createDivider());
        add(getControlTableSection());
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

    private Div createDivider() {
        Div hr = new Div();
        hr.getStyle().set("width", "100%").set("height", "1px").set("background-color", "var(--lumo-contrast-20pct)").set("margin", "1rem 0");
        return hr;
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
            updatePriceChart(chartTodayDiv, controlTableResponses, nordpoolPriceResponsesToday, this.control.getTimezone());
            updatePriceChart(chartTomorrowDiv, controlTableResponses, nordpoolPriceResponsesTomorrow, this.control.getTimezone());
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
        updatePriceChart(chartTodayDiv, controlTableResponses, nordpoolPriceResponses, this.control.getTimezone());

        chartTomorrowDiv = new Div();
        chartTomorrowDiv.setId("prices-tomorrow-chart");
        chartTomorrowDiv.setWidthFull();
        chartTomorrowDiv.setHeight("250px");
        if (!nordpoolPriceResponsesTomorrow.isEmpty()) {
            updatePriceChart(chartTomorrowDiv, controlTableResponses, nordpoolPriceResponsesTomorrow, this.control.getTimezone());
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
            String timezone
    ) {
        List<String> timestamps = new ArrayList<>();
        List<Double> nordpoolPrices = new ArrayList<>();
        List<Double> controlPrices = new ArrayList<>();

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

        // Convert to JsonArray
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

        String nordpoolLabel = t("controlTable.chart.nordpoolPrice");
        String controlLabel = t("controlTable.chart.controlPrice");
        String xAxisLabel = t("controlTable.chart.time");
        String yAxisLabel = t("controlTable.chart.price");
        String chartTitle = t("controlTable.chart.title");
        String nowLabel = t("controlTable.chart.now");

        chartDiv.getElement().executeJs("""
                    const container = this;
                    const nordpoolLabel = $3;
                    const controlLabel = $4;
                    const xAxisLabel = $5;
                    const yAxisLabel = $6;
                    const chartTitle = $7;
                    const nowLabel = $8;
                
                    function renderOrUpdate(dataX, dataNordpool, dataControl) {
                        if (!window.ApexCharts) {
                            const script = document.createElement('script');
                            script.src = 'https://cdn.jsdelivr.net/npm/apexcharts@3.49.0/dist/apexcharts.min.js';
                            script.onload = () => renderOrUpdate(dataX, dataNordpool, dataControl);
                            document.head.appendChild(script);
                            return;
                        }
                
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
                                    { name: controlLabel, data: dataControl, color: '#FF0000' }
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
                                { data: dataControl }
                            ], true);
                        }
                    }
                
                    renderOrUpdate($0, $1, $2);
                """,
                jsTimestamps, jsNordpoolPrices, jsControlPrices,
                nordpoolLabel, controlLabel, xAxisLabel, yAxisLabel, chartTitle, nowLabel
        );
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
