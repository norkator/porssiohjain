package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.ControlMode;
import com.nitramite.porssiohjain.services.ControlSchedulerService;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.NordpoolService;
import com.nitramite.porssiohjain.services.models.*;
import com.vaadin.flow.component.Text;
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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import elemental.json.Json;
import elemental.json.JsonArray;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route("controls/:controlId")
@PermitAll
public class ControlTableView extends VerticalLayout implements BeforeEnterObserver {

    private final ControlService controlService;
    private final DeviceService deviceService;
    private final ControlSchedulerService controlSchedulerService;
    private final NordpoolService nordpoolService;

    private final Grid<ControlDeviceResponse> deviceGrid = new Grid<>(ControlDeviceResponse.class, false);
    private final Grid<ControlTableResponse> controlTableGrid = new Grid<>(ControlTableResponse.class, false);

    private Long controlId;
    private ControlResponse control;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private Div chartDiv;

    @Autowired
    public ControlTableView(
            ControlService controlService,
            DeviceService deviceService,
            ControlSchedulerService controlSchedulerService,
            NordpoolService nordpoolService
    ) {
        this.controlService = controlService;
        this.deviceService = deviceService;
        this.controlSchedulerService = controlSchedulerService;
        this.nordpoolService = nordpoolService;

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
            add(new Paragraph("Error loading control: " + e.getMessage()));
        }
    }

    private void loadControl() {
        this.control = controlService.getControl(controlId);
    }

    private void renderView() {
        removeAll();

        add(new H2("Edit Control: " + control.getName()));

        NumberField maxPriceField = new NumberField("Max Price (snt)");
        maxPriceField.setValue(control.getMaxPriceSnt().doubleValue());

        NumberField dailyMinutes = new NumberField("Daily On Minutes");
        dailyMinutes.setValue(control.getDailyOnMinutes().doubleValue());

        NumberField taxPercentage = new NumberField("Tax %");
        taxPercentage.setValue(control.getTaxPercent().doubleValue());

        ComboBox<ControlMode> modeCombo = new ComboBox<>("Mode");
        modeCombo.setItems(ControlMode.values());
        modeCombo.setValue(control.getMode());
        modeCombo.setWidthFull();

        Checkbox manualToggle = new Checkbox("Manual On");
        manualToggle.setValue(control.getManualOn());

        Button saveButton = new Button("Save", e -> {
            try {
                control.setMaxPriceSnt(BigDecimal.valueOf(maxPriceField.getValue()));
                control.setDailyOnMinutes(dailyMinutes.getValue().intValue());
                control.setTaxPercent(BigDecimal.valueOf(taxPercentage.getValue()));
                control.setMode(modeCombo.getValue());
                if (control.getMode() == ControlMode.MANUAL) {
                    control.setManualOn(manualToggle.getValue());
                }

                controlService.updateControl(
                        controlId,
                        control.getName(),
                        control.getMaxPriceSnt(),
                        control.getDailyOnMinutes(),
                        control.getTaxPercent(),
                        control.getMode(),
                        control.getManualOn()
                );

                Notification.show("Saved successfully");
            } catch (Exception ex) {
                Notification.show("Failed to save: " + ex.getMessage());
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

        add(new H3("Device and device channels linked to this control:"));
        configureDeviceGrid();
        VerticalLayout deviceGridLayout = new VerticalLayout(deviceGrid);
        add(deviceGridLayout);

        add(createAddDeviceLayout());
        loadControlDevices();

        add(createDivider());
        add(getControlTableSection());
    }



    private void configureDeviceGrid() {
        deviceGrid.removeAllColumns();
        deviceGrid.addColumn(cd -> cd.getDevice().getDeviceName()).setHeader("Device Name");
        deviceGrid.addColumn(ControlDeviceResponse::getDeviceChannel).setHeader("Channel");
        deviceGrid.addColumn(cd -> cd.getDevice().getUuid()).setHeader("UUID");
        deviceGrid.addComponentColumn(cd -> {
            Button delete = new Button("Delete", e -> {
                controlService.deleteControlDevice(cd.getId());
                loadControlDevices();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return delete;
        }).setHeader("Actions");
        deviceGrid.setMaxHeight("200px");
    }

    private void loadControlDevices() {
        deviceGrid.setItems(controlService.getControlDevices(controlId));
    }

    private HorizontalLayout createAddDeviceLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>("Select Device");
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        deviceSelect.setItems(deviceService.getAllDevicesForControlId(controlId));

        NumberField channelField = new NumberField("Channel");
        channelField.setStep(1);

        Button addButton = new Button("Add Device", e -> {
            if (deviceSelect.getValue() != null && channelField.getValue() != null) {
                controlService.addDeviceToControl(controlId, deviceSelect.getValue().getId(), channelField.getValue().intValue());
                loadControlDevices();
            }
        });

        return new HorizontalLayout(deviceSelect, channelField, addButton);
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
        }).setHeader("Start Time");
        controlTableGrid.addColumn(entry -> {
            ZoneId zone = ZoneId.systemDefault();
            try {
                if (control.getTimezone() != null) {
                    zone = ZoneId.of(control.getTimezone());
                }
            } catch (Exception ignored) {
            }
            return ZonedDateTime.ofInstant(entry.getEndTime(), zone).format(formatter);
        }).setHeader("End Time");
        controlTableGrid.addColumn(ControlTableResponse::getPriceSnt).setHeader("Price (snt)");
        controlTableGrid.addColumn(ControlTableResponse::getStatus).setHeader("Status");
        controlTableGrid.setAllRowsVisible(true);

        Button recalcButton = new Button("Recalculate", e -> {
            controlSchedulerService.generateForControl(controlId);
            List<ControlTableResponse> controlTableResponses = controlSchedulerService.findByControlId(controlId);
            List<NordpoolPriceResponse> nordpoolPriceResponses = nordpoolService.getNordpoolPricesForControl(controlId);
            refreshControlTable();
            controlTableGrid.setItems(controlTableResponses);
            updatePriceChart(chartDiv, nordpoolPriceResponses, controlTableResponses, this.control.getTimezone());
        });
        recalcButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        List<ControlTableResponse> controlTableResponses = controlSchedulerService.findByControlId(controlId);
        List<NordpoolPriceResponse> nordpoolPriceResponses = nordpoolService.getNordpoolPricesForControl(controlId);
        refreshControlTable();

        VerticalLayout layout = new VerticalLayout(
                new HorizontalLayout(
                        new H3("Control Table"),
                        recalcButton
                ),
                new Div(new Text("Showing upcoming and currently in progress controls")),
                createPriceChart(controlTableResponses, nordpoolPriceResponses),
                controlTableGrid
        );
        layout.setWidthFull();
        return layout;
    }

    private void refreshControlTable() {
        List<ControlTableResponse> list = controlSchedulerService.findByControlId(controlId);
        controlTableGrid.setItems(list);
    }

    private Div createPriceChart(
            List<ControlTableResponse> controlTableResponses,
            List<NordpoolPriceResponse> nordpoolPriceResponses
    ) {
        chartDiv = new Div();
        chartDiv.setId("price-chart");
        chartDiv.setWidthFull();
        chartDiv.setHeight("400px");

        updatePriceChart(chartDiv, nordpoolPriceResponses, controlTableResponses, this.control.getTimezone());
        return chartDiv;
    }

    private void updatePriceChart(
            Div chartDiv,
            List<NordpoolPriceResponse> nordpoolPriceResponses,
            List<ControlTableResponse> controlTableResponses,
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

        chartDiv.getElement().executeJs("""
                    const container = this;
                
                    function renderOrUpdate(dataX, dataNordpool, dataControl) {
                        if (!window.ApexCharts) {
                            const script = document.createElement('script');
                            script.src = 'https://cdn.jsdelivr.net/npm/apexcharts@3.49.0/dist/apexcharts.min.js';
                            script.onload = () => renderOrUpdate(dataX, dataNordpool, dataControl);
                            document.head.appendChild(script);
                            return;
                        }
                
                        if (!container.chartInstance) {
                            const options = {
                                chart: {
                                    type: 'line',
                                    height: '400px',
                                    toolbar: { show: false },
                                    zoom: { enabled: false }
                                },
                                series: [
                                    { name: 'Nordpool Price', data: dataNordpool, color: '#0000FF' }, // blue
                                    { name: 'Control Active Price', data: dataControl, color: '#FF0000' } // red
                                ],
                                xaxis: { categories: dataX, title: { text: 'Time' }, labels: { rotate: -45 } },
                                yaxis: { title: { text: 'Price (snt)' } },
                                title: { text: 'Price over Time', align: 'center' },
                                stroke: { curve: 'smooth', width: 2 },
                                markers: { size: 4 },
                                tooltip: { shared: true }
                            };
                            container.chartInstance = new ApexCharts(container, options);
                            container.chartInstance.render();
                        } else {
                            container.chartInstance.updateOptions({ xaxis: { categories: dataX } });
                            container.chartInstance.updateSeries([
                                { data: dataNordpool },
                                { data: dataControl }
                            ], true);
                        }
                    }
                
                    renderOrUpdate($0, $1, $2);
                """, jsTimestamps, jsNordpoolPrices, jsControlPrices);
    }


}
