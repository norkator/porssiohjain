package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.ProductionSourceService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.ProductionHistoryResponse;
import com.nitramite.porssiohjain.services.models.ProductionSourceDeviceResponse;
import com.nitramite.porssiohjain.services.models.ProductionSourceResponse;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.PasswordField;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@PageTitle("Pörssiohjain - Production Source")
@Route("production-source/:sourceId")
@PermitAll
public class ProductionSourceView extends VerticalLayout implements BeforeEnterObserver {

    private final I18nService i18n;
    private final AuthService authService;
    private final ProductionSourceService productionSourceService;
    private final DeviceService deviceService;

    private Long sourceId;
    private Long accountId;

    private final Grid<ProductionSourceDeviceResponse> deviceGrid = new Grid<>(ProductionSourceDeviceResponse.class, false);

    private Div currentKwValue;
    private Div peakKwValue;
    private Div chartDiv;

    private ScheduledExecutorService scheduler;
    private UI ui;

    @Autowired
    public ProductionSourceView(
            AuthService authService,
            I18nService i18n,
            ProductionSourceService productionSourceService,
            DeviceService deviceService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.productionSourceService = productionSourceService;
        this.deviceService = deviceService;

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
        accountId = authService.authenticate(token).getId();
        String idParam = event.getRouteParameters().get("sourceId").orElse(null);
        if (idParam == null) {
            event.forwardTo(ProductionSourcesView.class);
            return;
        }
        sourceId = Long.parseLong(idParam);
        buildView();
    }

    private void buildView() {
        ProductionSourceResponse source = productionSourceService.getSource(accountId, sourceId);

        VerticalLayout card = createCard();

        card.add(new H3("Modify Production Source"));

        card.add(createSourceInfoSection(source));

        configureDeviceGrid();
        loadDevices();
        card.add(deviceGrid, createAddDeviceLayout());

        card.add(createCurrentStatsRow(source));

        chartDiv = createChartContainer();
        card.add(chartDiv);

        updateProductionChart(source);

        add(card);
    }

    private Component createSourceInfoSection(ProductionSourceResponse s) {
        TextField uuid = new TextField("UUID");
        uuid.setValue(s.getUuid().toString());
        uuid.setReadOnly(true);

        TextField name = new TextField("Name");
        name.setValue(s.getName());

        Checkbox enabled = new Checkbox("Enabled");
        enabled.setValue(s.isEnabled());

        TextField appId = new TextField("App ID");
        PasswordField appSecret = new PasswordField("App Secret");
        EmailField email = new EmailField("Email");
        PasswordField password = new PasswordField("Password");
        TextField stationId = new TextField("Station ID");

        Button save = new Button("Save", e -> {
            productionSourceService.updateSource(
                    accountId,
                    sourceId,
                    name.getValue(),
                    enabled.getValue(),
                    appId.getValue(),
                    appSecret.getValue(),
                    email.getValue(),
                    password.getValue(),
                    stationId.getValue()
            );
            Notification.show("Saved");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout form = new FormLayout(
                uuid, name, enabled,
                appId, appSecret, email, password, stationId
        );
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        Div wrap = styledBox();
        wrap.add(form, save);
        return wrap;
    }

    private void configureDeviceGrid() {
        deviceGrid.addColumn(d -> d.getDevice().getDeviceName()).setHeader("Device");
        deviceGrid.addColumn(ProductionSourceDeviceResponse::getDeviceChannel).setHeader("Channel");

        deviceGrid.addComponentColumn(d -> {
            Button delete = new Button("Delete", e -> {
                productionSourceService.removeDevice(sourceId, d.getId());
                loadDevices();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return delete;
        });
    }

    private void loadDevices() {
        deviceGrid.setItems(productionSourceService.getSourceDevices(sourceId));
    }

    private Component createAddDeviceLayout() {
        ComboBox<DeviceResponse> deviceSelect = new ComboBox<>("Device");
        deviceSelect.setItems(deviceService.getAllDevices(accountId));
        deviceSelect.setItemLabelGenerator(DeviceResponse::getDeviceName);
        NumberField channel = new NumberField("Channel");
        Button add = new Button("Add", e -> {
            productionSourceService.addDevice(
                    accountId,
                    sourceId,
                    deviceSelect.getValue().getId(),
                    channel.getValue().intValue()
            );
            loadDevices();
        });
        FormLayout layout = new FormLayout(deviceSelect, channel, add);
        layout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 3));
        return layout;
    }

    private Component createCurrentStatsRow(ProductionSourceResponse s) {
        currentKwValue = bigStatBox("Current kW", s.getCurrentKw() + " kW");
        peakKwValue = bigStatBox("Peak kW", s.getPeakKw() + " kW");
        return new HorizontalLayout(currentKwValue, peakKwValue);
    }

    private Div createChartContainer() {
        Div div = new Div();
        div.setWidthFull();
        div.setHeight("400px");
        div.getStyle().set("background", "var(--lumo-contrast-5pct)");
        return div;
    }

    private void updateProductionChart(ProductionSourceResponse source) {
        List<ProductionHistoryResponse> history = productionSourceService.getProductionHistory(sourceId, 24);
        List<String> timestamps = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

        for (ProductionHistoryResponse h : history) {
            timestamps.add(fmt.format(h.getCreatedAt()));
            values.add(h.getKilowatts().doubleValue());
        }

        JsonArray jsTimestamps = Json.createArray();
        JsonArray jsValues = Json.createArray();

        for (int i = 0; i < timestamps.size(); i++) {
            jsTimestamps.set(i, timestamps.get(i));
            jsValues.set(i, values.get(i));
        }

        chartDiv.getElement().executeJs("""
                    const container = this;
                    if (!window.ApexCharts) {
                        const script = document.createElement('script');
                        script.src = 'https://cdn.jsdelivr.net/npm/apexcharts';
                        script.onload = () => container.dispatchEvent(new Event('apex-ready'));
                        document.head.appendChild(script);
                        return;
                    }
                    new ApexCharts(container, {
                        chart: { type: 'line', height: 400 },
                        series: [{ name: 'kW', data: $1 }],
                        xaxis: { categories: $0 }
                    }).render();
                """, jsTimestamps, jsValues);
    }

    private VerticalLayout createCard() {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        card.getStyle().set("border-radius", "12px");
        card.getStyle().set("padding", "32px");
        card.getStyle().set("background-color", "var(--lumo-base-color)");
        return card;
    }

    private Div styledBox() {
        Div d = new Div();
        d.getStyle()
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("background-color", "var(--lumo-contrast-5pct)");
        return d;
    }

    private Div bigStatBox(String label, String value) {
        Div wrap = new Div();
        wrap.getStyle().set("text-align", "center");
        wrap.add(new H3(label), new H1(value));
        return wrap;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.ui = attachEvent.getUI();
        startAutoRefresh();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (scheduler != null) scheduler.shutdownNow();
        super.onDetach(detachEvent);
    }

    private void startAutoRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            ProductionSourceResponse updated = productionSourceService.getSource(accountId, sourceId);
            ui.access(() -> {
                currentKwValue.setText(updated.getCurrentKw() + " kW");
                peakKwValue.setText(updated.getPeakKw() + " kW");
                updateProductionChart(updated);
            });
        }, 0, 30, TimeUnit.SECONDS);
    }

}
