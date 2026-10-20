package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.enums.AccountTier;
import com.nitramite.porssiohjain.entity.enums.ContractType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcDevicesService;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiLoginService;
import com.nitramite.porssiohjain.services.mitsubishihome.MitsubishiMelCloudHomeDevicesService;
import com.nitramite.porssiohjain.services.mitsubishihome.MitsubishiMelCloudHomeLoginService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcDevicesService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaLoginService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListCreationViewsTest {
    private final AuthService auth = mock(AuthService.class);
    private final I18nService i18n = mock(I18nService.class, invocation -> invocation.getArgument(0));
    private final AccountLimitService limits = mock(AccountLimitService.class);
    private final WeatherControlService weather = mock(WeatherControlService.class);
    private final SiteService sites = mock(SiteService.class);
    private MockedStatic<ViewAuthUtils> accounts;
    private UI ui;
    private VaadinSession session;

    @BeforeEach
    void setup() {
        ui = new UI();
        session = mock(VaadinSession.class);
        UI.setCurrent(ui);
        VaadinSession.setCurrent(session);
        var registry = mock(com.vaadin.flow.server.StreamResourceRegistry.class);
        when(registry.getTargetURI(any())).thenReturn(java.net.URI.create("/test-download"));
        when(session.getResourceRegistry()).thenReturn(registry);
        accounts = mockStatic(ViewAuthUtils.class);
        accounts.when(() -> ViewAuthUtils.getAuthenticatedAccount(eq(auth), anyString()))
                .thenReturn(AccountEntity.builder().id(7L).build());
        when(limits.getTier(7L)).thenReturn(AccountTier.FREE);
        when(limits.getEffectiveDeviceLimit(7L)).thenReturn(10);
        when(limits.getEffectiveSiteLimit(7L)).thenReturn(1);
        when(limits.getEffectiveControlLimit(7L)).thenReturn(10);
        when(limits.getEffectiveWeatherControlLimit(7L)).thenReturn(10);
        when(limits.getEffectiveProductionSourceLimit(7L)).thenReturn(10);
    }

    @AfterEach
    void cleanup() {
        accounts.close();
        VaadinSession.setCurrent(null);
        UI.setCurrent(null);
    }

    @Test
    void listsGrowWithRowsAndKeepCreationFieldsInsideTheirOwnedDialog() {
        List<VerticalLayout> views = List.of(
                new DeviceView(mock(DeviceService.class), mock(ControlService.class), mock(DeviceAcCommandLogService.class),
                        auth, mock(FileService.class), mock(FactoryProvisioningService.class), i18n,
                        mock(ToshibaLoginService.class), mock(ToshibaAcDevicesService.class),
                        mock(MitsubishiLoginService.class), mock(MitsubishiAcDevicesService.class),
                        mock(MitsubishiMelCloudHomeLoginService.class), mock(MitsubishiMelCloudHomeDevicesService.class), limits,
                        mock(com.nitramite.porssiohjain.services.tuya.TuyaService.class)),
                new ControlsView(mock(ControlService.class), mock(ControlSchedulerService.class), limits, auth, i18n),
                new WeatherControlsView(weather, limits, sites, auth, i18n),
                new PowerLimitsView(mock(PowerLimitService.class), auth, i18n),
                new ProductionSourcesView(mock(ProductionSourceService.class), limits, auth, i18n),
                new SitesView(sites, auth, i18n, limits),
                new ElectricityContractsView(auth, i18n, mock(ElectricityContractRepository.class),
                        mock(AccountRepository.class), mock(DemoAccountGuard.class)));
        for (VerticalLayout view : views) {
            Grid<?> grid = descendants(view).filter(Grid.class::isInstance).map(Grid.class::cast).findFirst().orElseThrow();
            assertTrue(grid.isAllRowsVisible(), view.getClass().getSimpleName());
            assertNull(grid.getStyle().get("max-height"));
            assertNull(view.getHeight());
            assertNull(view.getStyle().get("overflow"));
            Component card = grid.getParent().orElseThrow();
            assertFalse(descendants(card).anyMatch(TextField.class::isInstance));
            DesktopCreateDialog dialog = dialog(view);
            assertSame(view, dialog.getParent().orElseThrow());
            assertFalse(dialog.isOpened());
            DesktopCreateDialog.creationButton(view).click();
            assertTrue(dialog.isOpened());
            assertTrue(descendants(dialog).anyMatch(TextField.class::isInstance));
        }
    }

    @Test
    void consecutiveModeShowsHintAndGeneratesScheduleWhenCreated() {
        ControlService controls = mock(ControlService.class);
        ControlSchedulerService scheduler = mock(ControlSchedulerService.class);
        ControlsView view = new ControlsView(controls, scheduler, limits, auth, i18n);
        DesktopCreateDialog dialog = dialog(view);
        dialog.openButton().click();
        @SuppressWarnings("unchecked")
        var mode = (com.vaadin.flow.component.combobox.ComboBox<com.nitramite.porssiohjain.entity.enums.ControlMode>)
                descendants(dialog).filter(c -> c instanceof com.vaadin.flow.component.combobox.ComboBox<?> box
                        && box.getLabel().equals("control.field.mode")).findFirst().orElseThrow();
        var hint = descendants(dialog).filter(com.nitramite.porssiohjain.views.components.InfoBox.class::isInstance)
                .findFirst().orElseThrow();
        assertFalse(hint.isVisible());
        mode.setValue(com.nitramite.porssiohjain.entity.enums.ControlMode.CHEAPEST_CONSECUTIVE_HOURS);
        assertTrue(hint.isVisible());
        assertTrue(hint.getElement().getTextRecursively().contains("control.consecutiveHint"));
        mode.setValue(com.nitramite.porssiohjain.entity.enums.ControlMode.CHEAPEST_HOURS);
        assertFalse(hint.isVisible());
        mode.setValue(com.nitramite.porssiohjain.entity.enums.ControlMode.CHEAPEST_CONSECUTIVE_HOURS);

        descendants(dialog).filter(TextField.class::isInstance).map(TextField.class::cast).findFirst().orElseThrow()
                .setValue("Load");
        descendants(dialog).filter(com.vaadin.flow.component.textfield.NumberField.class::isInstance)
                .map(com.vaadin.flow.component.textfield.NumberField.class::cast).forEach(field -> field.setValue(10.0));
        var minutes = descendants(dialog).filter(com.vaadin.flow.component.textfield.IntegerField.class::isInstance)
                .map(com.vaadin.flow.component.textfield.IntegerField.class::cast).findFirst().orElseThrow();
        assertEquals(15, minutes.getStep());
        minutes.setValue(180);
        when(controls.createControl(eq(7L), eq("Load"), anyString(), any(), any(), eq(180), any(),
                eq(com.nitramite.porssiohjain.entity.enums.ControlMode.CHEAPEST_CONSECUTIVE_HOURS), eq(false), eq(false)))
                .thenReturn(com.nitramite.porssiohjain.entity.ControlEntity.builder().id(42L).build());
        footerButton(dialog, "control.button.create").click();
        verify(scheduler).generateForControl(42L);
        assertFalse(dialog.isOpened());
    }

    @Test
    void tuyaPlatformShowsPickerLocksSensorTypeAndRequiresSelection() {
        DeviceView view = new DeviceView(mock(DeviceService.class), mock(ControlService.class), mock(DeviceAcCommandLogService.class),
                auth, mock(FileService.class), mock(FactoryProvisioningService.class), i18n,
                mock(ToshibaLoginService.class), mock(ToshibaAcDevicesService.class),
                mock(MitsubishiLoginService.class), mock(MitsubishiAcDevicesService.class),
                mock(MitsubishiMelCloudHomeLoginService.class), mock(MitsubishiMelCloudHomeDevicesService.class), limits,
                mock(com.nitramite.porssiohjain.services.tuya.TuyaService.class));
        DesktopCreateDialog dialog = dialog(view);
        @SuppressWarnings("unchecked")
        var platform = (com.vaadin.flow.component.combobox.ComboBox<com.nitramite.porssiohjain.entity.enums.DevicePlatform>)
                descendants(dialog).filter(c -> c instanceof com.vaadin.flow.component.combobox.ComboBox<?> box
                        && box.getLabel().equals("device.field.platform")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var type = (com.vaadin.flow.component.combobox.ComboBox<com.nitramite.porssiohjain.entity.enums.DeviceType>)
                descendants(dialog).filter(c -> c instanceof com.vaadin.flow.component.combobox.ComboBox<?> box
                        && box.getLabel().equals("device.grid.type")).findFirst().orElseThrow();
        TuyaDevicePicker picker = descendants(dialog).filter(TuyaDevicePicker.class::isInstance)
                .map(TuyaDevicePicker.class::cast).findFirst().orElseThrow();
        assertFalse(picker.isVisible());
        platform.setValue(com.nitramite.porssiohjain.entity.enums.DevicePlatform.TUYA_SMART);
        assertTrue(picker.isVisible());
        assertEquals(com.nitramite.porssiohjain.entity.enums.DeviceType.TEMPERATURE_SENSOR, type.getValue());
        assertTrue(type.isReadOnly());
        Button save = descendants(dialog).filter(c -> c instanceof Button button && button.getText().equals("device.button.add"))
                .map(Button.class::cast).findFirst().orElseThrow();
        assertFalse(save.isEnabled());
        platform.setValue(com.nitramite.porssiohjain.entity.enums.DevicePlatform.GENERIC_MQTT);
        assertFalse(picker.isVisible());
        assertFalse(type.isReadOnly());
        assertTrue(save.isEnabled());
    }

    @Test
    void validationAndFailedSaveKeepDialogOpenAndSuccessfulSaveRefreshesList() {
        WeatherControlsView view = new WeatherControlsView(weather, limits, sites, auth, i18n);
        DesktopCreateDialog dialog = dialog(view);
        dialog.openButton().click();
        Button submit = footerButton(dialog, "weatherControl.button.create");
        submit.click();
        assertTrue(dialog.isOpened());
        verify(weather, never()).createWeatherControl(any(), any(), any());

        TextField name = descendants(dialog).filter(TextField.class::isInstance).map(TextField.class::cast)
                .findFirst().orElseThrow();
        name.setValue("Weather control");
        var site = com.nitramite.porssiohjain.services.models.SiteResponse.builder().id(9L).name("Home").build();
        @SuppressWarnings("unchecked")
        var siteField = (com.vaadin.flow.component.combobox.ComboBox<com.nitramite.porssiohjain.services.models.SiteResponse>)
                descendants(dialog).filter(com.vaadin.flow.component.combobox.ComboBox.class::isInstance).findFirst().orElseThrow();
        siteField.setItems(site);
        siteField.setValue(site);
        when(weather.createWeatherControl(7L, "Weather control", 9L)).thenThrow(new IllegalArgumentException("Rejected"));
        submit.click();
        assertTrue(dialog.isOpened());
        assertEquals("Weather control", name.getValue());
        verify(weather, times(1)).getAllWeatherControls(7L);

        doReturn(null).when(weather).createWeatherControl(7L, "Weather control", 9L);
        submit.click();
        assertFalse(dialog.isOpened());
        assertTrue(name.isEmpty());
        assertTrue(siteField.isEmpty());
        assertFalse(DesktopFormState.isDirty(view));
        verify(weather, times(2)).getAllWeatherControls(7L);
        verify(weather, times(2)).createWeatherControl(7L, "Weather control", 9L);
    }

    @Test
    void tuyaQrGenerationRequiresUserCodeBeforeContactingTuya() {
        var tuya = mock(com.nitramite.porssiohjain.services.tuya.TuyaService.class);
        when(tuya.connectionStatus(7L)).thenReturn(new com.nitramite.porssiohjain.services.tuya.TuyaService.ConnectionStatus(false, ""));
        TuyaConnectionDialog dialog = new TuyaConnectionDialog(tuya, auth, i18n, () -> { });
        dialog.open();
        TextField code = descendants(dialog).filter(TextField.class::isInstance).map(TextField.class::cast)
                .findFirst().orElseThrow();
        Button generate = descendants(dialog).filter(c -> c instanceof Button button && button.getText().equals("tuya.qr.generate"))
                .map(Button.class::cast).findFirst().orElseThrow();

        assertTrue(code.isRequiredIndicatorVisible());
        assertEquals("tuya.error.userCode", code.getErrorMessage());
        assertFalse(generate.isEnabled());
        code.setValue("   ");
        assertFalse(generate.isEnabled());
        generate.click();
        verify(tuya, never()).beginLogin(any(), anyString());

        code.setValue("my-user-code");
        assertTrue(generate.isEnabled());
        assertFalse(code.isInvalid());
        code.clear();
        assertFalse(generate.isEnabled());
        dialog.close();
        dialog.open();
        assertFalse(generate.isEnabled());
    }

    @Test
    void creationDialogExplainsReachedAccountLimitAndKeepsSubmissionDisabled() {
        when(limits.getEffectiveWeatherControlLimit(7L)).thenReturn(0);
        WeatherControlsView view = new WeatherControlsView(weather, limits, sites, auth, i18n);
        DesktopCreateDialog dialog = dialog(view);
        dialog.openButton().click();
        assertTrue(dialog.isOpened());
        assertFalse(footerButton(dialog, "weatherControl.button.create").isEnabled());
        assertTrue(dialog.getElement().getTextRecursively().contains("accountLimits.weatherControls"));
        verify(weather, never()).createWeatherControl(any(), any(), any());
    }

    @Test
    void siteLimitShowsUpgradeGuidanceAndDisablesCreationWhileAllowingExistingSiteEdits() {
        when(limits.getSiteCount(7L)).thenReturn(1L);
        var site = com.nitramite.porssiohjain.services.models.SiteResponse.builder().id(9L).name("Home")
                .type(com.nitramite.porssiohjain.entity.enums.SiteType.HOME).timezone("Europe/Helsinki")
                .operationState(com.nitramite.porssiohjain.entity.enums.SiteOperationState.NORMAL).enabled(true).build();
        when(sites.getAllSites(7L)).thenReturn(List.of(site));
        when(sites.getStoredSiteWeatherForecast(7L, 9L, null, null)).thenReturn(
                com.nitramite.porssiohjain.services.models.SiteWeatherForecastResponse.builder().points(List.of()).build());
        var view = new SitesView(sites, auth, i18n, limits);
        DesktopCreateDialog dialog = dialog(view);
        dialog.openButton().click();
        Button submit = footerButton(dialog, "sites.button.create");
        assertFalse(submit.isEnabled());
        assertTrue(dialog.getElement().getTextRecursively().contains("accountLimits.sites"));
        assertTrue(descendants(dialog).anyMatch(component -> component instanceof Button button
                && button.getText().equals("sites.limitUpgrade") && button.isVisible()));
        descendants(dialog).filter(component -> component instanceof Button button
                && button.getText().equals("sites.limitUpgrade")).map(Button.class::cast).findFirst().orElseThrow().click();
        assertFalse(dialog.isOpened());
        @SuppressWarnings("unchecked")
        Grid<com.nitramite.porssiohjain.services.models.SiteResponse> grid = (Grid<com.nitramite.porssiohjain.services.models.SiteResponse>)
                descendants(view).filter(Grid.class::isInstance).findFirst().orElseThrow();
        clickRow(grid, site);
        assertTrue(footerButton(dialog, "sites.button.update").isEnabled());
        dialog.close();
        dialog.openButton().click();
        assertFalse(footerButton(dialog, "sites.button.create").isEnabled());
    }

    @Test
    void contractSelectionOpensEditDialogAndAddStartsANewContract() {
        var repository = mock(ElectricityContractRepository.class);
        var accountRepository = mock(AccountRepository.class);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(AccountEntity.builder().id(7L).build()));
        ElectricityContractEntity contract = new ElectricityContractEntity();
        contract.setId(42L);
        contract.setName("Existing");
        contract.setType(ContractType.values()[0]);
        when(repository.findByAccountId(7L)).thenReturn(List.of(contract));
        var view = new ElectricityContractsView(auth, i18n, repository, accountRepository, mock(DemoAccountGuard.class));
        @SuppressWarnings("unchecked")
        Grid<ElectricityContractEntity> grid = (Grid<ElectricityContractEntity>) descendants(view)
                .filter(Grid.class::isInstance).findFirst().orElseThrow();
        clickRow(grid, contract);
        DesktopCreateDialog dialog = dialog(view);
        assertTrue(dialog.isOpened());
        assertEquals("electricityContracts.button.update", dialog.getHeaderTitle());
        dialog.close();
        clickRow(grid, contract);
        assertTrue(dialog.isOpened());
        assertEquals("electricityContracts.button.update", dialog.getHeaderTitle());
        dialog.close();
        dialog.openButton().click();
        assertTrue(dialog.isOpened());
        assertEquals("electricityContracts.button.addNew", dialog.getHeaderTitle());
        assertTrue(grid.getSelectedItems().isEmpty());
        TextField name = descendants(dialog).filter(TextField.class::isInstance).map(TextField.class::cast)
                .findFirst().orElseThrow();
        assertTrue(name.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private <T> void clickRow(Grid<T> grid, T row) {
        var event = mock(com.vaadin.flow.component.grid.ItemClickEvent.class);
        when(event.getItem()).thenReturn(row);
        com.vaadin.flow.component.ComponentUtil.fireEvent(grid, event);
    }

    private DesktopCreateDialog dialog(Component view) {
        return view.getChildren().filter(DesktopCreateDialog.class::isInstance)
                .map(DesktopCreateDialog.class::cast).findFirst().orElseThrow();
    }

    private Button footerButton(DesktopCreateDialog target, String label) {
        return target.getFooter().getElement().getChildren().flatMap(element -> element.getComponent().stream())
                .filter(component -> component instanceof Button button && button.getText().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
