package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import com.nitramite.porssiohjain.entity.enums.WaterMeterNotificationFrequency;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.WaterMeterService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.PasswordField;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaterMetersViewTest {
    private static final String PORTAL = "https://wmd.wrm-systems.fi/water-company";
    private final AuthService auth = mock();
    private final WaterMeterService service = mock();
    private final AccountEntity account = AccountEntity.builder().id(7L).build();
    private final WaterMeterService.Meter meter = new WaterMeterService.Meter(9L, "Home", PORTAL,
            true, new BigDecimal("140.8"), Instant.parse("2026-10-22T09:00:00Z"), Instant.parse("2026-10-22T09:00:00Z"), null,
            WaterMeterProvider.WRM_SYSTEMS_WMD, WaterMeterService.NotificationSettings.defaults());
    private MockedStatic<ViewAuthUtils> accounts;
    private I18nService i18n;
    private UI ui;

    @BeforeEach
    void setup() {
        ui = new UI();
        UI.setCurrent(ui);
        UI.getCurrent().setLocale(Locale.US);
        accounts = mockStatic(ViewAuthUtils.class);
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(account);
        when(service.list(7L)).thenReturn(List.of(meter));
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("translations/messages");
        messages.setDefaultEncoding("UTF-8");
        i18n = new I18nService(messages);
    }

    @AfterEach
    void cleanup() {
        accounts.close();
        VaadinSession.setCurrent(null);
        UI.setCurrent(null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void showsAccountScopedTotalsAndEditsNeverPopulateSavedCredentials() {
        var view = new WaterMetersView(auth, i18n, service);
        Grid<WaterMeterService.Meter> grid = grid(view);
        assertEquals(List.of(meter), grid.getListDataView().getItems().toList());
        var renderer = (ComponentRenderer<?, WaterMeterService.Meter>) grid.getColumns().getLast().getRenderer();
        Component actions = renderer.createComponent(meter);
        buttons(actions).filter(button -> button.getText().equals("Edit water meter")).findFirst().orElseThrow().click();
        TextField payer = fields(view).filter(field -> field.getLabel().startsWith("Payer number")).findFirst().orElseThrow();
        PasswordField password = descendants(view).filter(PasswordField.class::isInstance).map(PasswordField.class::cast)
                .findFirst().orElseThrow();
        assertEquals("", payer.getValue());
        assertEquals("", password.getValue());
        buttons(view).filter(button -> button.getText().equals("Save")).findFirst().orElseThrow().click();
        verify(service).save(7L, 9L, new WaterMeterService.Settings("Home", WaterMeterProvider.WRM_SYSTEMS_WMD,
                PORTAL, "", "", true, WaterMeterService.NotificationSettings.defaults()));
        assertFalse(DesktopFormState.isDirty(view));
    }

    @Test
    void finnishFormHasTheRequestedNamesAndCreateSendsBothCredentials() {
        UI.getCurrent().setLocale(Locale.of("fi", "FI"));
        var view = new WaterMetersView(auth, i18n, service);
        assertTrue(view.getElement().getTextRecursively().contains("Vesimittarit"));
        TextField name = fields(view).filter(field -> field.getLabel().equals("Nimi")).findFirst().orElseThrow();
        TextField payer = fields(view).filter(field -> field.getLabel().equals("Maksajan numero")).findFirst().orElseThrow();
        PasswordField password = descendants(view).filter(PasswordField.class::isInstance).map(PasswordField.class::cast)
                .findFirst().orElseThrow();
        assertEquals("Vesimittarin numero", password.getLabel());
        name.setValue("Koti");
        fields(view).filter(field -> field.getLabel().equals("Portaalin osoite")).findFirst().orElseThrow().setValue(PORTAL);
        payer.setValue("test-payer");
        password.setValue("test-meter");
        buttons(view).filter(button -> button.getText().equals("Tallenna")).findFirst().orElseThrow().click();
        verify(service).save(7L, null, new WaterMeterService.Settings("Koti", WaterMeterProvider.WRM_SYSTEMS_WMD,
                PORTAL, "test-payer", "test-meter", true, WaterMeterService.NotificationSettings.defaults()));
        assertTrue(payer.isEmpty());
        assertTrue(password.isEmpty());
    }

    @Test
    void guardReroutesUnauthenticatedAccessAndDoesNotLoadAnyMeters() {
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(null);
        var view = new WaterMetersView(auth, i18n, service);
        BeforeEnterEvent event = mock();
        view.beforeEnter(event);
        accounts.verify(() -> ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, auth));
        verifyNoInteractions(service);
        assertFalse(DesktopCreateDialog.creationButton(view).isEnabled());
    }

    @Test
    @SuppressWarnings("unchecked")
    void demoAccountsCannotSaveOrPoll() {
        account.setDemo(true);
        var view = new WaterMetersView(auth, i18n, service);
        assertFalse(DesktopCreateDialog.creationButton(view).isEnabled());
        buttons(view).filter(button -> button.getText().equals("Save")).findFirst().orElseThrow().click();
        var renderer = (ComponentRenderer<?, WaterMeterService.Meter>) grid(view).getColumns().getLast().getRenderer();
        assertTrue(buttons(renderer.createComponent(meter)).filter(button -> !button.getText().equals("Graph")).noneMatch(Button::isEnabled));
        verify(service, never()).save(any(), any(), any());
        verify(service, never()).pollNow(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminSupportCanEditAndPollTheEffectiveAccountsMeters() {
        accounts.when(ViewAuthUtils::isImpersonating).thenReturn(true);
        when(service.pollNow(7L, 9L)).thenReturn(meter);
        var view = new WaterMetersView(auth, i18n, service);
        assertTrue(DesktopCreateDialog.creationButton(view).isEnabled());
        var renderer = (ComponentRenderer<?, WaterMeterService.Meter>) grid(view).getColumns().getLast().getRenderer();
        var actions = renderer.createComponent(meter);
        buttons(actions).filter(button -> button.getText().equals("Read now")).findFirst().orElseThrow().click();
        verify(service).pollNow(7L, 9L);
        buttons(actions).filter(button -> button.getText().equals("Edit water meter")).findFirst().orElseThrow().click();
        buttons(view).filter(button -> button.getText().equals("Save")).findFirst().orElseThrow().click();
        verify(service).save(eq(7L), eq(9L), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void graphShowsOnlySelectedMetersStoredHistoryWithoutReadingTheProvider() {
        var history = List.of(new WaterMeterService.Measurement(1L, new BigDecimal("140.8"),
                Instant.parse("2026-10-22T09:00:00Z")));
        when(service.history(7L, 9L)).thenReturn(history);
        var view = new WaterMetersView(auth, i18n, service);
        var renderer = (ComponentRenderer<?, WaterMeterService.Meter>) grid(view).getColumns().getLast().getRenderer();
        buttons(renderer.createComponent(meter)).filter(button -> button.getText().equals("Graph"))
                .findFirst().orElseThrow().click();
        var grids = descendants(view).filter(Grid.class::isInstance).map(Grid.class::cast).toList();
        assertEquals(2, grids.size());
        assertEquals(history, grids.getLast().getListDataView().getItems().toList());
        assertTrue(descendants(view).anyMatch(com.nitramite.porssiohjain.views.components.DeviceMeasurementHistoryChart.class::isInstance));
        verify(service).history(7L, 9L);
        verify(service, never()).pollNow(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void weeklyNotificationFieldsAreSavedWithTheToggleTimeAndTimezone() {
        var view = new WaterMetersView(auth, i18n, service);
        fields(view).filter(field -> field.getLabel().equals("Name")).findFirst().orElseThrow().setValue("Home");
        fields(view).filter(field -> field.getLabel().equals("Portal address")).findFirst().orElseThrow().setValue(PORTAL);
        fields(view).filter(field -> field.getLabel().startsWith("Payer number")).findFirst().orElseThrow().setValue("payer");
        descendants(view).filter(PasswordField.class::isInstance).map(PasswordField.class::cast).findFirst().orElseThrow().setValue("meter");
        var enabled = descendants(view).filter(com.vaadin.flow.component.checkbox.Checkbox.class::isInstance)
                .map(com.vaadin.flow.component.checkbox.Checkbox.class::cast)
                .filter(field -> field.getLabel().equals("Enable push notifications")).findFirst().orElseThrow();
        var frequency = (com.vaadin.flow.component.combobox.ComboBox<WaterMeterNotificationFrequency>) descendants(view)
                .filter(com.vaadin.flow.component.combobox.ComboBox.class::isInstance)
                .map(com.vaadin.flow.component.combobox.ComboBox.class::cast)
                .filter(field -> field.getLabel().equals("Notification frequency")).findFirst().orElseThrow();
        var weekday = (com.vaadin.flow.component.combobox.ComboBox<java.time.DayOfWeek>) descendants(view)
                .filter(com.vaadin.flow.component.combobox.ComboBox.class::isInstance)
                .map(com.vaadin.flow.component.combobox.ComboBox.class::cast)
                .filter(field -> field.getLabel().equals("Weekday")).findFirst().orElseThrow();
        assertFalse(weekday.isVisible());
        assertFalse(frequency.isEnabled());
        enabled.setValue(true);
        frequency.setValue(WaterMeterNotificationFrequency.WEEKLY);
        assertTrue(weekday.isVisible());
        assertTrue(frequency.isEnabled());
        weekday.setValue(java.time.DayOfWeek.FRIDAY);
        descendants(view).filter(com.vaadin.flow.component.timepicker.TimePicker.class::isInstance)
                .map(com.vaadin.flow.component.timepicker.TimePicker.class::cast).findFirst().orElseThrow()
                .setValue(java.time.LocalTime.of(18, 30));
        buttons(view).filter(button -> button.getText().equals("Save")).findFirst().orElseThrow().click();
        verify(service).save(7L, null, new WaterMeterService.Settings("Home", WaterMeterProvider.WRM_SYSTEMS_WMD,
                PORTAL, "payer", "meter", true, new WaterMeterService.NotificationSettings(true,
                WaterMeterNotificationFrequency.WEEKLY, java.time.LocalTime.of(18, 30), java.time.DayOfWeek.FRIDAY, "Europe/Helsinki")));
    }

    @SuppressWarnings("unchecked")
    private static Grid<WaterMeterService.Meter> grid(Component root) {
        return (Grid<WaterMeterService.Meter>) descendants(root).filter(Grid.class::isInstance).findFirst().orElseThrow();
    }

    private static Stream<TextField> fields(Component root) {
        return descendants(root).filter(TextField.class::isInstance).map(TextField.class::cast);
    }

    private static Stream<Button> buttons(Component root) {
        return descendants(root).filter(Button.class::isInstance).map(Button.class::cast);
    }

    private static Stream<Component> descendants(Component root) {
        Stream<Component> children = root.getChildren();
        if (root instanceof com.vaadin.flow.component.dialog.Dialog dialog) {
            children = Stream.concat(children, dialog.getFooter().getElement().getChildren()
                    .flatMap(element -> element.getComponent().stream()));
        }
        return Stream.concat(Stream.of(root), children.flatMap(WaterMetersViewTest::descendants));
    }
}
