package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.heating.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeatingPlannerSiteNavigationTest {
    private final AuthService auth = mock();
    private final SiteRepository sites = mock();
    private final DeviceRepository devices = mock();
    private final HeatingPlannerConfigurationService configuration = mock();
    private final HeatingPlannerPlanService plans = mock();
    private final HeatingPlannerActiveControlService active = mock();
    private final DeviceMeasurementService readings = mock();
    private final HeatingPlannerThermalModelService models = mock();
    private final UI navigationUi = mock();
    private MockedStatic<ViewAuthUtils> accounts;
    private MockedStatic<Notification> notifications;
    private HeatingPlannerView view;

    @BeforeEach
    void setUp() {
        UI.setCurrent(new UI());
        UI.getCurrent().setLocale(Locale.US);
        VaadinSession.setCurrent(mock(VaadinSession.class));
        accounts = mockStatic(ViewAuthUtils.class);
        notifications = mockStatic(Notification.class);
        notifications.when(() -> Notification.show(anyString())).thenReturn(mock(Notification.class));
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth))
                .thenReturn(AccountEntity.builder().id(7L).build());
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("translations/messages");
        messages.setDefaultEncoding("UTF-8");
        var measurements = mock(HeatingPlannerMeasurementService.class);
        when(measurements.latestFreshRoomTemperature(any(), any()))
                .thenReturn(HeatingPlannerMeasurementService.LatestMeasurement.missing());
        when(measurements.latestFreshFloorTemperature(any(), any()))
                .thenReturn(HeatingPlannerMeasurementService.LatestMeasurement.missing());
        when(models.resolveSaved(anyLong(), anyLong(), anyString(), any(), any()))
                .thenAnswer(invocation -> new HeatingPlannerThermalModelService.ModelResolution(
                        invocation.getArgument(3), false, 0, BigDecimal.ZERO, "Configured defaults"));
        when(active.readiness(anyLong(), anyLong(), any())).thenReturn(
                new HeatingPlannerActiveControlService.Readiness(false, false, List.of(), null, null, null, null));
        when(configuration.changeNotifications(anyLong(), anyLong()))
                .thenReturn(new HeatingPlannerConfigurationService.ChangeNotifications(false, false));
        when(sites.findByAccountId(7L)).thenReturn(List.of(site(8L, "Home", "Europe/Helsinki")));
        view = spy(new HeatingPlannerView(auth, new I18nService(messages), new HeatingPlanSimulationService(),
                sites, mock(SiteWeatherRepository.class), devices, mock(NordpoolRepository.class),
                mock(ElectricityContractRepository.class), configuration, measurements, models, active, plans,
                mock(ControlPriceService.class), mock(PushNotificationService.class), readings));
        doReturn(Optional.of(navigationUi)).when(view).getUI();
    }

    @AfterEach
    void tearDown() {
        accounts.close();
        notifications.close();
        UI.setCurrent(null);
        VaadinSession.setCurrent(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "0", "-1", "999"})
    void invalidOrUnownedSiteNeverLoadsOrCalculatesPlanner(String siteId) {
        var event = navigation(siteId);
        view.beforeEnter(event);
        verify(event).forwardTo(HeatingPlannerView.class);
        verifyNoInteractions(devices, configuration, plans, active, models, readings);
        assertThat(view.getComponentCount()).isZero();
    }

    @Test
    void signedOutUserCannotOpenAPlannerDeepLink() {
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(null);
        var event = navigation("8");
        view.beforeEnter(event);
        verify(event).forwardTo(LoginView.class);
        verifyNoInteractions(sites, devices, configuration, plans, active, readings);
    }

    @Test
    void twoSitesLoadTheirOwnRoomsSettingsReadingsAndSaveToTheSelectedSite() {
        SiteEntity home = site(8L, "Home", "Europe/Helsinki");
        SiteEntity cottage = site(9L, "Cottage", "America/Los_Angeles");
        when(sites.findByAccountId(7L)).thenReturn(List.of(home, cottage));
        when(sites.findByIdAndAccountId(8L, 7L)).thenReturn(Optional.of(home));
        when(sites.findByIdAndAccountId(9L, 7L)).thenReturn(Optional.of(cottage));
        DeviceEntity homeSensor = sensor(10L, "Home sensor");
        DeviceEntity cottageSensor = sensor(11L, "Cottage sensor");
        when(devices.findByAccountIdOrderByIdAsc(7L)).thenReturn(List.of(homeSensor, cottageSensor));
        when(configuration.configuration(7L, 8L)).thenReturn(config("Home room", 10L, "25.5"));
        when(configuration.configuration(7L, 9L)).thenReturn(config("Cottage room", 11L, "10"));
        when(readings.latestForAccount(eq(7L), any())).thenReturn(List.of(reading(homeSensor), reading(cottageSensor)));

        view.beforeEnter(navigation("8"));
        assertThat(sitePicker().getValue()).isEqualTo(home);
        assertThat(sitePicker().getItemLabelGenerator().apply(home)).isEqualTo("Home · Europe/Helsinki");
        assertThat(tax().getValue()).isEqualTo(25.5);
        verify(configuration, never()).configuration(7L, 9L);
        verify(configuration, never()).preferredSiteId(anyLong());
        verifyNoInteractions(readings);
        assertReadingsFor(10L);
        button("Save rooms").click();
        verify(configuration).save(eq(7L), eq(8L), any(), argThat(rooms ->
                rooms.size() == 1 && rooms.getFirst().name().equals("Home room")));

        sitePicker().setValue(cottage);
        verify(navigationUi).navigate(eq(HeatingPlannerView.class), argThat((RouteParameters parameters) ->
                parameters.get("siteId").orElseThrow().equals("9")));
        assertThat(sitePicker().getValue()).isEqualTo(home);
        verify(configuration, never()).setDefaultSite(anyLong(), anyLong());
        // Vaadin may reuse the component after guarded navigation succeeds.
        view.beforeEnter(navigation("9"));
        assertThat(sitePicker().getValue()).isEqualTo(cottage);
        assertThat(view.getElement().getTextRecursively()).doesNotContain("Home room", "Change site", "Vaihda kohdetta");
        assertThat(tax().getValue()).isEqualTo(10.0);
        assertReadingsFor(11L);
        tax().setValue(12.0);
        verify(configuration).saveSettings(eq(7L), eq(9L), argThat(settings ->
                settings.taxPercent().compareTo(new BigDecimal("12")) == 0));
        button("Save rooms").click();
        verify(configuration).save(eq(7L), eq(9L), any(), argThat(rooms ->
                rooms.size() == 1 && rooms.getFirst().name().equals("Cottage room")));
        verify(plans, atLeastOnce()).persistSimulatedPlan(eq(7L), eq(8L), argThat(result -> result.keySet().equals(java.util.Set.of("Home room"))));
        verify(plans, atLeastOnce()).persistSimulatedPlan(eq(7L), eq(9L), argThat(result -> result.keySet().equals(java.util.Set.of("Cottage room"))));
        assertThat((String) ReflectionTestUtils.invokeMethod(view, "formatInstant", Instant.parse("2026-01-15T12:00:00Z")))
                .isEqualTo("2026-01-15 04:00:00");
    }

    @Test
    void noSitesShowsCreateSiteActionWithoutLoadingPlanner() {
        when(sites.findByAccountId(7L)).thenReturn(List.of());
        var event = navigation(null);
        view.beforeEnter(event);
        assertThat(view.getElement().getTextRecursively()).contains("Create a site");
        assertThat(descendants(view).filter(ComboBox.class::isInstance)).isEmpty();
        verifyNoInteractions(configuration, plans, active, models, readings);
        button("Open Sites").click();
        verify(navigationUi).navigate(SitesView.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fi"})
    void rootOpensDefaultWithEnglishDropdownAndSavesPreferenceExplicitly(String language) {
        UI.getCurrent().setLocale(Locale.forLanguageTag(language));
        var home = site(8L, "Home", "Europe/Helsinki");
        var cottage = site(9L, "Cottage", "Europe/Helsinki");
        when(sites.findByAccountId(7L)).thenReturn(List.of(home, cottage));
        when(sites.findByIdAndAccountId(9L, 7L)).thenReturn(Optional.of(cottage));
        when(configuration.preferredSiteId(7L)).thenReturn(Optional.of(9L));
        when(configuration.defaultSiteId(7L)).thenReturn(Optional.empty());
        when(configuration.configuration(7L, 9L)).thenReturn(config("Cottage room", null, "25.5"));
        view.beforeEnter(navigation(null));
        assertThat(sitePicker().getValue()).isEqualTo(cottage);
        assertThat(sitePicker().getLabel()).isEqualTo("Site");
        assertThat(view.getElement().getTextRecursively()).contains("Make default", "Comfort schedules")
                .doesNotContain("Vaihda kohdetta", "Kohde");
        verify(configuration, never()).setDefaultSite(anyLong(), anyLong());
        Button makeDefault = button("Make default");
        assertThat(makeDefault.isEnabled()).isTrue();
        makeDefault.click();
        verify(configuration).setDefaultSite(7L, 9L);
        assertThat(makeDefault.isEnabled()).isFalse();
        assertThat(view.getElement().getTextRecursively()).contains("Default site");
        sitePicker().clear();
        assertThat(sitePicker().getValue()).isEqualTo(cottage);
    }

    @Test
    void explicitlySavedDefaultIsMarkedAndSingleSiteStillOpensWithoutAMenu() {
        var home = site(8L, "Home", "Europe/Helsinki");
        when(sites.findByIdAndAccountId(8L, 7L)).thenReturn(Optional.of(home));
        when(configuration.preferredSiteId(7L)).thenReturn(Optional.of(8L));
        when(configuration.defaultSiteId(7L)).thenReturn(Optional.of(8L));
        when(configuration.configuration(7L, 8L)).thenReturn(config("Home room", null, "25.5"));
        view.beforeEnter(navigation(null));
        assertThat(sitePicker().getValue().getId()).isEqualTo(8L);
        assertThat(button("Make default").isEnabled()).isFalse();
    }

    private void assertReadingsFor(Long deviceId) {
        Details recent = descendants(view).filter(Details.class::isInstance).map(Details.class::cast)
                .filter(details -> details.getSummaryText().equals("Recent sensor readings")).findFirst().orElseThrow();
        recent.setOpened(true);
        Grid<?> grid = descendants(recent).filter(Grid.class::isInstance).map(Grid.class::cast).findFirst().orElseThrow();
        assertThat(grid.getListDataView().getItems().map(row -> (Long) ReflectionTestUtils.invokeMethod(row, "deviceId")))
                .containsExactly(deviceId);
    }

    private HeatingPlannerConfigurationService.Configuration config(String roomName, Long sensorId, String tax) {
        var room = new HeatingPlannerConfigurationService.RoomConfiguration(roomName, HeatingPlannerHeatSourceType.OTHER,
                false, new BigDecimal("2"), new BigDecimal("21"), new BigDecimal("20"), new BigDecimal("23"),
                new BigDecimal("27"), new BigDecimal("29"), new BigDecimal("19"), null, sensorId, null);
        return new HeatingPlannerConfigurationService.Configuration(false, false, new BigDecimal("5"), BigDecimal.ZERO,
                new BigDecimal(tax), null, false, LocalTime.of(6, 0), LocalTime.of(22, 0), new BigDecimal("8"),
                45, 360, new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("0.25"), new BigDecimal("0.75"),
                false, LocalTime.of(22, 0), LocalTime.of(5, 0), List.of(room));
    }

    private DeviceEntity sensor(Long id, String name) {
        return DeviceEntity.builder().id(id).deviceName(name).deviceType(DeviceType.TEMPERATURE_SENSOR)
                .devicePlatform(DevicePlatform.TUYA_SMART).build();
    }

    private DeviceMeasurementEntity reading(DeviceEntity device) {
        return DeviceMeasurementEntity.builder().device(device).value(new BigDecimal("21"))
                .source(DevicePlatform.TUYA_SMART).sourceKey("temperature").measurementKey("temperature")
                .measurementType(DeviceMeasurementType.TEMPERATURE).measuredAt(Instant.now())
                .observedAt(Instant.now()).receivedAt(Instant.now()).build();
    }

    private SiteEntity site(Long id, String name, String timezone) {
        return SiteEntity.builder().id(id).name(name).timezone(timezone).build();
    }

    private BeforeEnterEvent navigation(String siteId) {
        var event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(siteId == null ? new RouteParameters()
                : new RouteParameters("siteId", siteId));
        return event;
    }

    @SuppressWarnings("unchecked")
    private ComboBox<SiteEntity> sitePicker() {
        return (ComboBox<SiteEntity>) descendants(view).filter(c -> c instanceof ComboBox<?> box
                && box.getLabel().equals("Site")).findFirst().orElseThrow();
    }

    private NumberField tax() {
        return descendants(view).filter(c -> c instanceof NumberField field && field.getLabel().equals("Market VAT (%)"))
                .map(NumberField.class::cast).findFirst().orElseThrow();
    }

    private Button button(String label) {
        return descendants(view).filter(c -> c instanceof Button button && button.getText().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
