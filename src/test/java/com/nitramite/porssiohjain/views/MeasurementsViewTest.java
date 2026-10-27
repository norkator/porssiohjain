package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.DeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.DeviceMeasurementRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceMeasurementService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.PublicMeasurementService;
import com.nitramite.porssiohjain.views.components.DeviceMeasurementHistoryChart;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MeasurementsViewTest {
    private final AuthService auth = mock();
    private final PublicMeasurementService publicMeasurements = mock();
    private final DeviceMeasurementRepository generic = mock();
    private final ZigbeeDeviceMeasurementRepository zigbee = mock();
    private final DeviceMeasurementService service = new DeviceMeasurementService(generic, zigbee);
    private final DeviceEntity tuyaDevice = DeviceEntity.builder().id(13L).deviceName("Living room")
            .devicePlatform(DevicePlatform.TUYA_SMART).build();
    private final Instant observed = Instant.parse("2026-09-20T21:30:00Z");
    private final DeviceMeasurementEntity snapshot = DeviceMeasurementEntity.builder().id(2L).device(tuyaDevice)
            .measurementType(DeviceMeasurementType.TEMPERATURE).measurementKey("temperature")
            .source(DevicePlatform.TUYA_SMART).sourceKey("va_temperature").unit("°C")
            .value(new BigDecimal("20.900")).observedAt(observed).receivedAt(observed).build();
    private MockedStatic<ViewAuthUtils> accounts;
    private I18nService i18n;

    @BeforeEach
    void setup() {
        UI.setCurrent(new UI());
        UI.getCurrent().setLocale(Locale.ENGLISH);
        VaadinSession.setCurrent(mock(VaadinSession.class));
        accounts = mockStatic(ViewAuthUtils.class);
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth))
                .thenReturn(AccountEntity.builder().id(7L).build());
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("translations/messages");
        messages.setDefaultEncoding("UTF-8");
        i18n = new I18nService(messages);
        when(publicMeasurements.ownerSettings(anyLong())).thenReturn(new PublicMeasurementService.OwnerSettings(null, List.of()));
    }

    @AfterEach
    void cleanup() {
        accounts.close();
        VaadinSession.setCurrent(null);
        UI.setCurrent(null);
    }

    @Test
    void groupsBothStoresByTypeAndKeepsOlderRetainedSensorsDiscoverable() {
        var device = DeviceEntity.builder().id(12L).deviceName("Bedroom")
                .devicePlatform(DevicePlatform.ANDROID_ZIGBEE).build();
        var temperature = ZigbeeDeviceMeasurementEntity.builder().id(1L).device(device)
                .measurementType(ZigbeeMeasurementType.TEMPERATURE).measurementKey("temperature")
                .value(new BigDecimal("21.5")).measuredAt(observed).receivedAt(observed).build();
        var humidity = ZigbeeDeviceMeasurementEntity.builder().id(3L).device(device)
                .measurementType(ZigbeeMeasurementType.HUMIDITY).measurementKey("humidity")
                .value(new BigDecimal("45")).measuredAt(observed).receivedAt(observed).build();
        when(generic.findLatestDistinctMeasurements(7L, Instant.EPOCH)).thenReturn(List.of(snapshot));
        when(zigbee.findLatestDistinctMeasurements(7L, Instant.EPOCH)).thenReturn(List.of(temperature, humidity));

        var view = new MeasurementsView(auth, i18n, service, publicMeasurements);

        assertThat(descendants(view).filter(H3.class::isInstance).map(H3.class::cast).map(H3::getText))
                .containsExactly("Public sharing", "Temperature (2)", "Humidity (1)");
        assertThat(grids(view).getFirst().getListDataView().getItems()).hasSize(2);
        assertThat(grids(view).getLast().getListDataView().getItems()).hasSize(1);
        TextField search = descendants(view).filter(TextField.class::isInstance).map(TextField.class::cast)
                .findFirst().orElseThrow();
        search.setValue("va_temperature");
        assertThat(grids(view)).hasSize(1);
        assertThat(grids(view).getFirst().getListDataView().getItems()).containsExactly(snapshot);
        search.setValue("not a device");
        assertThat(grids(view)).isEmpty();
        assertThat(view.getElement().getTextRecursively()).contains("No measurements match your search.");
        search.clear();
        assertThat(grids(view)).hasSize(2);
        verify(generic).findLatestDistinctMeasurements(7L, Instant.EPOCH);
        verify(zigbee).findLatestDistinctMeasurements(7L, Instant.EPOCH);
    }

    @Test
    @SuppressWarnings("unchecked")
    void plotUsesAccountScopedHistoryAndDistinguishesCloudSnapshotsFromSensorReportTimes() {
        when(generic.findLatestDistinctMeasurements(7L, Instant.EPOCH)).thenReturn(List.of(snapshot));
        when(generic.findTop500ByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(
                7L, 13L, DeviceMeasurementType.TEMPERATURE, "temperature")).thenReturn(List.of(snapshot));
        var view = new MeasurementsView(auth, i18n, service, publicMeasurements);
        var timezone = (ComboBox<String>) descendants(view).filter(ComboBox.class::isInstance).findFirst().orElseThrow();
        timezone.setValue("America/Los_Angeles");
        Grid<DeviceMeasurementEntity> grid = grids(view).getFirst();
        var renderer = (ComponentRenderer<?, DeviceMeasurementEntity>) grid.getColumns().getLast().getRenderer();
        ((Button) renderer.createComponent(snapshot)).click();

        Dialog dialog = descendants(view).filter(Dialog.class::isInstance).map(Dialog.class::cast)
                .findFirst().orElseThrow();
        assertThat(dialog.isOpened()).isTrue();
        assertThat(dialog.getHeaderTitle()).contains("Living room", "Temperature", "temperature");
        assertThat(dialog.getElement().getTextRecursively()).contains("up to 500", "America/Los_Angeles",
                "sensor report time is unknown");
        assertThat(descendants(dialog).filter(DeviceMeasurementHistoryChart.class::isInstance)).hasSize(1);
        assertThat(grids(dialog).getFirst().getListDataView().getItems()).containsExactly(snapshot);
        assertThat(snapshot.getMeasuredAt()).isNull();
        assertThat(snapshot.getObservedAt()).isEqualTo(observed);
        verify(generic).findTop500ByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(
                7L, 13L, DeviceMeasurementType.TEMPERATURE, "temperature");
        verify(zigbee).findTop500ByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                7L, 13L, ZigbeeMeasurementType.TEMPERATURE, "temperature");
        dialog.close();
        assertThat(descendants(view).filter(Dialog.class::isInstance)).isEmpty();
    }

    @Test
    void refreshUsesTheCurrentEffectiveAccountAndReplacesThePreviousReadings() {
        when(generic.findLatestDistinctMeasurements(7L, Instant.EPOCH)).thenReturn(List.of(snapshot));
        var view = new MeasurementsView(auth, i18n, service, publicMeasurements);
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth))
                .thenReturn(AccountEntity.builder().id(42L).build());

        descendants(view).filter(c -> c instanceof Button b && b.getText().equals("Refresh readings"))
                .map(Button.class::cast).findFirst().orElseThrow().click();

        verify(generic).findLatestDistinctMeasurements(42L, Instant.EPOCH);
        verify(zigbee).findLatestDistinctMeasurements(42L, Instant.EPOCH);
        assertThat(grids(view)).isEmpty();
        assertThat(view.getElement().getTextRecursively()).contains("No recorded measurements yet.");
    }

    @Test
    void unauthenticatedViewDoesNotReadMeasurementsAndUsesTheRouteGuard() {
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(null);
        var view = new MeasurementsView(auth, i18n, service, publicMeasurements);
        BeforeEnterEvent event = mock();
        view.beforeEnter(event);
        accounts.verify(() -> ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, auth));
        verifyNoInteractions(generic, zigbee);
    }

    @Test
    void restoresFinnishLocaleBeforeCreatingTheView() {
        when(VaadinSession.getCurrent().getAttribute(Locale.class)).thenReturn(Locale.of("fi"));
        var view = new MeasurementsView(auth, i18n, service, publicMeasurements);
        assertThat(view.getElement().getTextRecursively()).contains("Mittaukset", "Päivitä lukemat",
                "Ei vielä tallennettuja mittauksia");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicCheckboxPersistsOnlyItsPropertyAndExposesTheAccountShareLink() {
        accounts.when(ViewAuthUtils::isImpersonating).thenReturn(true);
        when(generic.findLatestDistinctMeasurements(7L, Instant.EPOCH)).thenReturn(List.of(snapshot));
        var selected = new PublicMeasurementService.PublishedMeasurement(13L, "Living room",
                DeviceMeasurementType.TEMPERATURE, "temperature");
        UUID token = UUID.randomUUID();
        when(publicMeasurements.ownerSettings(7L)).thenReturn(
                new PublicMeasurementService.OwnerSettings(null, List.of()),
                new PublicMeasurementService.OwnerSettings(token, List.of(selected)));
        var view = new MeasurementsView(auth, i18n, service, publicMeasurements);
        var column = grids(view).getFirst().getColumnByKey("public");
        var renderer = (ComponentRenderer<?, DeviceMeasurementEntity>) column.getRenderer();
        var checkbox = (Checkbox) renderer.createComponent(snapshot);
        assertThat(checkbox.isEnabled()).isTrue();
        assertThat(checkbox.getValue()).isFalse();
        checkbox.setValue(true);
        verify(publicMeasurements).setPublished(7L, 13L, DeviceMeasurementType.TEMPERATURE, "temperature", true);
        assertThat(descendants(view).filter(Anchor.class::isInstance).map(Anchor.class::cast).map(Anchor::getHref))
                .containsExactly("public-measurements/" + token);
        assertThat(view.getElement().getTextRecursively()).contains("Remove from public page");
    }

    @Test
    void publicationCanBeRemovedEvenWhenTheDeviceHasNoRetainedReadings() {
        var selected = new PublicMeasurementService.PublishedMeasurement(13L, "Living room",
                DeviceMeasurementType.TEMPERATURE, "temperature");
        when(publicMeasurements.ownerSettings(7L)).thenReturn(
                new PublicMeasurementService.OwnerSettings(UUID.randomUUID(), List.of(selected)),
                new PublicMeasurementService.OwnerSettings(UUID.randomUUID(), List.of()));
        var view = new MeasurementsView(auth, i18n, service, publicMeasurements);
        assertThat(grids(view)).isEmpty();
        descendants(view).filter(c -> c instanceof Button b && b.getText().equals("Remove from public page"))
                .map(Button.class::cast).findFirst().orElseThrow().click();
        verify(publicMeasurements).setPublished(7L, 13L, DeviceMeasurementType.TEMPERATURE, "temperature", false);
    }

    @SuppressWarnings("unchecked")
    private List<Grid<DeviceMeasurementEntity>> grids(Component root) {
        return descendants(root).filter(Grid.class::isInstance).map(c -> (Grid<DeviceMeasurementEntity>) c).toList();
    }

    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
