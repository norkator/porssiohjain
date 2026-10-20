package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AdminClientCallLogService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminDeviceConnectionMapViewTest {
    @Test
    void onlyAdminsLoadCallDataAndRefreshRechecksEffectiveAccount() {
        var auth = mock(AuthService.class);
        var calls = mock(AdminClientCallLogService.class);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("translations/messages");
        UI ui = new UI();
        ui.setLocale(Locale.ENGLISH);
        UI.setCurrent(ui);
        try (var accounts = mockStatic(ViewAuthUtils.class)) {
            accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(null);
            var anonymous = new AdminDeviceConnectionMapView(auth, new I18nService(messages), calls);
            assertEquals(0, anonymous.getChildren().count());
            accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth))
                    .thenReturn(AccountEntity.builder().id(2L).admin(false).build());
            var regular = new AdminDeviceConnectionMapView(auth, new I18nService(messages), calls);
            assertEquals(0, regular.getChildren().count());
            verifyNoInteractions(calls);

            var admin = AccountEntity.builder().id(1L).admin(true).build();
            accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(admin);
            var recorded = new AdminClientCallLogService();
            recorded.recordControlDeviceCall("device-a", "192.0.2.1", "/control/{deviceUuid}");
            when(calls.findLatestClients()).thenReturn(recorded.findLatestClients());
            var view = new AdminDeviceConnectionMapView(auth, new I18nService(messages), calls);
            assertTrue(children(view).filter(component -> !component.getElement().isTextNode())
                    .anyMatch(component -> component.getElement().getTag().equals("device-connection-map")));
            verify(calls).findLatestClients();

            var refresh = children(view).filter(component -> !component.getElement().isTextNode())
                    .filter(component -> component.getElement().getTag().equals("span"))
                    .filter(component -> component.getElement().getText().isEmpty()).findFirst().orElseThrow();
            refresh.getElement().getNode().getFeature(com.vaadin.flow.internal.nodefeature.ElementListenerMap.class)
                    .fireEvent(new com.vaadin.flow.dom.DomEvent(refresh.getElement(), "client-call-refresh",
                            com.vaadin.flow.internal.JacksonUtils.createObjectNode()));
            verify(calls, times(2)).findLatestClients();
            accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth))
                    .thenReturn(AccountEntity.builder().id(2L).admin(false).build());
            refresh.getElement().getNode().getFeature(com.vaadin.flow.internal.nodefeature.ElementListenerMap.class)
                    .fireEvent(new com.vaadin.flow.dom.DomEvent(refresh.getElement(), "client-call-refresh",
                            com.vaadin.flow.internal.JacksonUtils.createObjectNode()));
            verify(calls, times(2)).findLatestClients();
        } finally {
            UI.setCurrent(null);
        }
    }

    private Stream<Component> children(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::children));
    }
}
