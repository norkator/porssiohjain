package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.ControlSchedulerService;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.NordpoolService;
import com.nitramite.porssiohjain.services.models.*;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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
import java.util.List;

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

        // Editable fields
        NumberField maxPriceField = new NumberField("Max Price (snt)");
        maxPriceField.setValue(control.getMaxPriceSnt().doubleValue());

        NumberField dailyMinutes = new NumberField("Daily On Minutes");
        dailyMinutes.setValue(control.getDailyOnMinutes().doubleValue());

        Button saveButton = new Button("Save", e -> {
            try {
                control.setMaxPriceSnt(BigDecimal.valueOf(maxPriceField.getValue()));
                control.setDailyOnMinutes(dailyMinutes.getValue().intValue());
                controlService.updateControl(
                        controlId, control.getName(), control.getMaxPriceSnt(), control.getDailyOnMinutes()
                );
                Notification.show("Saved successfully");
            } catch (Exception ex) {
                Notification.show("Failed to save: " + ex.getMessage());
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(new HorizontalLayout(maxPriceField, dailyMinutes, saveButton));

        add(new H3("Device and device channels linked to this control:"));
        configureDeviceGrid();
        add(deviceGrid);

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
    }

    private void loadControlDevices() {
        deviceGrid.setItems(controlService.getControlDevices(controlId));
    }

    private HorizontalLayout createAddDeviceLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>("Select Device");
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        deviceSelect.setItems(deviceService.getAllDevices(controlId));

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
            updatePriceChart(chartDiv, nordpoolPriceResponses, this.control.getTimezone());
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

        updatePriceChart(chartDiv, nordpoolPriceResponses, this.control.getTimezone());
        return chartDiv;
    }

    private void updatePriceChart(
            Div chartDiv,
            List<NordpoolPriceResponse> priceList,
            String timezone
    ) {
        List<String> timestamps = new ArrayList<>();
        List<Double> prices = new ArrayList<>();

        ZoneId zone = ZoneId.systemDefault();
        try {
            if (timezone != null) {
                zone = ZoneId.of(timezone);
            }
        } catch (Exception ignored) {}

        DateTimeFormatter jsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(zone);

        for (NordpoolPriceResponse entry : priceList) {
            timestamps.add(jsFormatter.format(entry.getDeliveryStart()));
            prices.add(entry.getPriceFiWithTax().doubleValue());
        }

        JsonArray jsTimestamps = Json.createArray();
        for (int i = 0; i < timestamps.size(); i++) {
            jsTimestamps.set(i, timestamps.get(i));
        }

        JsonArray jsPrices = Json.createArray();
        for (int i = 0; i < prices.size(); i++) {
            jsPrices.set(i, prices.get(i));
        }

        chartDiv.getElement().executeJs("""
                    const container = this;
                
                    function renderOrUpdate(dataX, dataY) {
                        if (!window.ApexCharts) {
                            const script = document.createElement('script');
                            script.src = 'https://cdn.jsdelivr.net/npm/apexcharts@3.49.0/dist/apexcharts.min.js';
                            script.onload = () => renderOrUpdate(dataX, dataY);
                            document.head.appendChild(script);
                            return;
                        }
                
                        // reuse existing chart if available
                        if (!container.chartInstance) {
                            const options = {
                                chart: {
                                    type: 'line',
                                    height: '400px',
                                    toolbar: { show: false },
                                    zoom: { enabled: false }
                                },
                                series: [{ name: 'Price (snt)', data: dataY }],
                                xaxis: { categories: dataX, title: { text: 'Time' }, labels: { rotate: -45 } },
                                yaxis: { title: { text: 'Price (snt)' } },
                                title: { text: 'Price over Time', align: 'center' },
                                stroke: { curve: 'smooth', width: 2 }
                            };
                            container.chartInstance = new ApexCharts(container, options);
                            container.chartInstance.render();
                        } else {
                            container.chartInstance.updateOptions({ xaxis: { categories: dataX } });
                            container.chartInstance.updateSeries([{ data: dataY }], true);
                        }
                    }
                
                    renderOrUpdate($0, $1);
                """, jsTimestamps, jsPrices);
    }


}
