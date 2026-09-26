package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class DesktopDeviceStatusTest {
    @Test
    void countsEitherConnectionOnceAndRefreshesUsingEffectiveAccount() {
        UI ui = new UI();
        ui.setLocale(Locale.ENGLISH);
        UI.setCurrent(ui);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("translations/messages");
        var auth = mock(AuthService.class);
        var devices = mock(DeviceService.class);
        var effectiveAccount = mock(AccountEntity.class);
        when(effectiveAccount.getId()).thenReturn(42L);
        var both = DeviceResponse.builder().id(3L).apiOnline(true).mqttOnline(true).build();
        when(devices.getAllDevices(42L)).thenReturn(List.of(
                DeviceResponse.builder().id(1L).apiOnline(true).build(),
                DeviceResponse.builder().id(2L).mqttOnline(true).shared(true).build(),
                both, both,
                DeviceResponse.builder().id(4L).build()));
        try (var accounts = mockStatic(ViewAuthUtils.class)) {
            accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(effectiveAccount);
            var widget = new DesktopDeviceStatus(auth, devices, new I18nService(messages), () -> {});
            assertTrue(widget.getElement().getTag().equals("button"));
            assertTrue(widget.getChildren().noneMatch(c -> c.getElement().getTag().equals("button")));
            assertTrue(widget.getElement().getTextRecursively().contains("3 / 4 online"));
            assertTrue(widget.getElement().getTextRecursively().contains("1 offline"));
            when(devices.getAllDevices(42L)).thenReturn(List.of());
            widget.refresh();
            assertTrue(widget.getElement().getTextRecursively().contains("0 / 0 online"));
            accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(null);
            widget.refresh();
            assertTrue(widget.getElement().getTextRecursively().contains("—"));
            verify(devices, times(2)).getAllDevices(42L);
        } finally {
            UI.setCurrent(null);
        }
    }
}
