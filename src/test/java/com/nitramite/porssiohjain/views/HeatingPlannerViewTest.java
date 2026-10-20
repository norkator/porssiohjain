package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.heating.*;
import com.nitramite.porssiohjain.views.components.HeatingPlanChart;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.RouteParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeatingPlannerViewTest {
    private final AuthService auth = mock(AuthService.class);
    private MockedStatic<ViewAuthUtils> accounts;

    @BeforeEach
    void setUp() {
        UI.setCurrent(new UI());
        VaadinSession.setCurrent(mock(VaadinSession.class));
        accounts = mockStatic(ViewAuthUtils.class);
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth))
                .thenReturn(AccountEntity.builder().id(7L).build());
    }

    @AfterEach
    void tearDown() {
        accounts.close();
        VaadinSession.setCurrent(null);
        UI.setCurrent(null);
    }

    @ParameterizedTest
    @CsvSource({"20.00, Europe/Helsinki, false, false", "18.00, Europe/Helsinki, false, false", "20.00, America/Los_Angeles, false, false",
            "20.00, Europe/Helsinki, true, false", "20.00, Europe/Helsinki, false, true"})
    void manualPlanUsesCurrentReadingAndQuarterHourStepsAndExplainsHeatPumpRequest(String minimum, String timezone, boolean cloudSnapshot, boolean scheduled) {
        Instant measuredAt = Instant.now().minusSeconds(60);
        var sites = mock(SiteRepository.class);
        var devices = mock(DeviceRepository.class);
        var prices = mock(NordpoolRepository.class);
        var configuration = mock(HeatingPlannerConfigurationService.class);
        var measurements = mock(HeatingPlannerMeasurementService.class);
        var measurementHistory = mock(DeviceMeasurementService.class);
        var models = mock(HeatingPlannerThermalModelService.class);
        var activeControl = mock(HeatingPlannerActiveControlService.class);
        var plans = mock(HeatingPlannerPlanService.class);
        var combinedPrices = mock(ControlPriceService.class);
        var simulation = spy(new HeatingPlanSimulationService());
        var site = SiteEntity.builder().id(8L).name("Home").enabled(true).timezone(timezone).build();
        var pump = DeviceEntity.builder().id(9L).deviceName("Heat pump").deviceType(DeviceType.HEAT_PUMP).build();
        var sensor = DeviceEntity.builder().id(10L).deviceName("Room sensor").deviceType(DeviceType.TEMPERATURE_SENSOR)
                .devicePlatform(com.nitramite.porssiohjain.entity.enums.DevicePlatform.TUYA_SMART).build();
        when(sites.findByAccountId(7L)).thenReturn(List.of(site));
        when(sites.findByIdAndAccountId(8L, 7L)).thenReturn(java.util.Optional.of(site));
        when(devices.findByAccountIdOrderByIdAsc(7L)).thenReturn(List.of(pump, sensor));
        var comfortSchedule = scheduled ? new HeatingPlannerConfigurationService.ComfortScheduleConfiguration(12L, "Morning",
                new com.nitramite.porssiohjain.services.models.HeatingComfortSchedule(new BigDecimal("22"), List.of())) : null;
        when(configuration.comfortSchedules(7L, 8L)).thenReturn(scheduled ? List.of(comfortSchedule) : List.of());
        var room = new HeatingPlannerConfigurationService.RoomConfiguration("Living room",
                HeatingPlannerHeatSourceType.HEAT_PUMP, true, new BigDecimal("2.00"),
                new BigDecimal("21.00"), new BigDecimal(minimum), new BigDecimal("23.00"),
                new BigDecimal("27.00"), new BigDecimal("29.00"), new BigDecimal("19.00"), 9L, 10L, null, comfortSchedule);
        when(configuration.configuration(7L, 8L)).thenReturn(new HeatingPlannerConfigurationService.Configuration(
                true, true, new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, null, false,
                LocalTime.of(6, 0), LocalTime.of(22, 0), new BigDecimal("8.00"), 45, 360,
                new BigDecimal("5.00"), new BigDecimal("20.00"), new BigDecimal("0.25"), new BigDecimal("0.75"),
                false, LocalTime.of(22, 0), LocalTime.of(5, 0), List.of(room)));
        when(configuration.changeNotifications(7L, 8L))
                .thenReturn(new HeatingPlannerConfigurationService.ChangeNotifications(false, false));
        when(measurements.latestFreshRoomTemperature(any(), any())).thenReturn(
                new HeatingPlannerMeasurementService.LatestMeasurement(new BigDecimal("22.900"), cloudSnapshot ? null : measuredAt,
                        HeatingPlannerMeasurementService.Freshness.FRESH, measuredAt));
        when(measurements.latestFreshFloorTemperature(any(), any()))
                .thenReturn(HeatingPlannerMeasurementService.LatestMeasurement.missing());
        when(models.resolveSaved(eq(7L), eq(8L), eq("Living room"), any(), any()))
                .thenAnswer(invocation -> new HeatingPlannerThermalModelService.ModelResolution(
                        invocation.getArgument(3), false, 0, BigDecimal.ZERO, "Configured defaults"));
        when(activeControl.readiness(eq(7L), eq(8L), any())).thenReturn(
                new HeatingPlannerActiveControlService.Readiness(false, false, List.of(), null, null, null, null));
        when(combinedPrices.getCombinedPrice(any(), any(), any(), any())).thenReturn(BigDecimal.TEN);
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime start = LocalDate.now(zone).atStartOfDay(zone);
        List<NordpoolEntity> market = new ArrayList<>();
        for (ZonedDateTime period = start; period.isBefore(start.plusDays(2)); period = period.plusMinutes(15)) {
            NordpoolEntity price = new NordpoolEntity();
            price.setDeliveryStart(period.toInstant());
            price.setDeliveryEnd(period.plusMinutes(15).toInstant());
            market.add(price);
        }
        when(prices.findPricesBetween(anyString(), any(), any())).thenReturn(market);

        var view = new HeatingPlannerView(auth, mock(I18nService.class, invocation -> invocation.getArgument(0)),
                simulation, sites, mock(SiteWeatherRepository.class), devices, prices,
                mock(ElectricityContractRepository.class), configuration, measurements, models, activeControl, plans,
                combinedPrices, mock(PushNotificationService.class), measurementHistory);
        var navigation = mock(BeforeEnterEvent.class);
        when(navigation.getRouteParameters()).thenReturn(new RouteParameters("siteId", "8"));
        verifyNoInteractions(configuration, devices, simulation, models, plans, measurementHistory);
        view.beforeEnter(navigation);

        verify(models).resolveSaved(eq(7L), eq(8L), eq("Living room"), any(), any());
        verify(models, never()).learnAndResolve(any(), any(), any(), any(), any());
        verify(devices).findByAccountIdOrderByIdAsc(7L);
        verifyNoInteractions(measurementHistory);

        var request = ArgumentCaptor.forClass(HeatingPlanSimulationService.SimulationRequest.class);
        var calculatedAt = ArgumentCaptor.forClass(Instant.class);
        verify(simulation).simulate(request.capture(), calculatedAt.capture());
        assertThat(request.getValue().settings().comfortSchedule()).isEqualTo(scheduled ? comfortSchedule.schedule() : null);
        var roomGrid = descendants(view).filter(Grid.class::isInstance).map(Grid.class::cast)
                .filter(grid -> grid.hasClassName("heating-planner-rooms")).findFirst().orElseThrow();
        assertThat(roomGrid.getWidth()).isEqualTo("100%");
        roomGrid.getColumns().forEach(column -> {
            Grid.Column<?> typed = (Grid.Column<?>) column;
            assertThat(typed.getFlexGrow()).isZero();
            assertThat(typed.getWidth()).endsWith("px");
        });
        if (scheduled) {
            Object editableRoom = roomGrid.getListDataView().getItems().findFirst().orElseThrow();
            var high = new HeatingPlannerConfigurationService.ComfortScheduleConfiguration(13L, "High",
                    new com.nitramite.porssiohjain.services.models.HeatingComfortSchedule(new BigDecimal("35"), List.of()));
            ReflectionTestUtils.invokeMethod(editableRoom, "setComfortSchedule", high);
            assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(editableRoom, "maximumPreheatFloorTemperature"))
                    .isEqualByComparingTo("27");
            assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(editableRoom, "normalFloorTemperature"))
                    .isEqualByComparingTo("27");
            ReflectionTestUtils.invokeMethod(editableRoom, "setComfortSchedule", comfortSchedule);
            assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(editableRoom, "normalFloorTemperature"))
                    .isEqualByComparingTo("24");
        }
        assertThat(request.getValue().settings().step()).isEqualTo(Duration.ofMinutes(15));
        assertThat(request.getValue().initialRoomTemperature()).isEqualByComparingTo("22.900");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, HeatingPlanSimulationService.SimulationResult>> results =
                ArgumentCaptor.forClass(Map.class);
        verify(plans).persistSimulatedPlan(eq(7L), eq(8L), results.capture());
        var current = results.getValue().get("Living room").points().getFirst();
        assertThat(current.time()).isBeforeOrEqualTo(calculatedAt.getValue());
        assertThat(current.time().plusSeconds(900)).isAfter(calculatedAt.getValue());
        assertThat(current.mode()).isEqualTo(HeatingPlanSimulationService.OperatingMode.NORMAL);
        assertThat(current.roomTemperature()).isEqualByComparingTo("22.82");
        Object row = descendants(view).filter(Grid.class::isInstance).map(Grid.class::cast)
                .flatMap(grid -> grid.getListDataView().getItems())
                .filter(item -> item.getClass().getSimpleName().equals("HeatPumpEvidenceRow"))
                .findFirst().orElseThrow();
        assertThat((String) ReflectionTestUtils.invokeMethod(row, "roomSensor"))
                .contains(cloudSnapshot ? "22.900 °C · cloud snapshot fetched at" : "22.900 °C fresh");
        if (cloudSnapshot) {
            assertThat((String) ReflectionTestUtils.invokeMethod(row, "roomSensor")).contains("sensor report time unknown");
        }
        assertThat((String) ReflectionTestUtils.invokeMethod(row, "predictedRoomTemperature"))
                .contains("22.82 °C after this 15 min step");
        assertThat((String) ReflectionTestUtils.invokeMethod(row, "currentRequest"))
                .contains((scheduled ? "22" : "21.00") + " °C requested setpoint", "Maintain the comfort target")
                .doesNotContain("below", "floor");
        String text = view.getElement().getTextRecursively();
        assertThat(text).contains("15 min steps", "minimum is configured separately")
                .doesNotContain("1 h simulation step", "target - 1 °C");
        verify(plans).chartHistory(7L, 8L, start.toInstant(), calculatedAt.getValue());
        assertThat(descendants(view).filter(HeatingPlanChart.class::isInstance).count()).isEqualTo(2);
        assertThat(text).contains("whole local day", "saved predictions, not measurements", "updates every minute");

        Details recent = descendants(view).filter(Details.class::isInstance).map(Details.class::cast)
                .filter(details -> details.getSummaryText().equals("Recent sensor readings")).findFirst().orElseThrow();
        recent.setOpened(true);
        verify(measurementHistory).latestForAccount(eq(7L), any());
        recent.setOpened(false);
        recent.setOpened(true);
        verify(measurementHistory, times(1)).latestForAccount(eq(7L), any());
        descendants(recent).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals("Refresh readings")).findFirst().orElseThrow().click();
        verify(measurementHistory, times(2)).latestForAccount(eq(7L), any());
    }

    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
