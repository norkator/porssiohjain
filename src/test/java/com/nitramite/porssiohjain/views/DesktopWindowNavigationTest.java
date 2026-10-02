package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.ServiceNoticeResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesktopWindowNavigationTest {
    @Test
    void detailAndBackReuseFrameAndTaskWhileOtherFeaturesKeepTheirWindows() {
        UI ui = new UI();
        ui.setLocale(Locale.ENGLISH);
        UI.setCurrent(ui);
        var auth = mock(AuthService.class);
        var account = mock(AccountEntity.class);
        var devices = mock(DeviceService.class);
        var notices = mock(ServiceNoticeService.class);
        var messages = new org.springframework.context.support.ResourceBundleMessageSource();
        messages.setBasename("translations/messages");
        var i18n = new I18nService(messages);
        when(notices.getNotice(any())).thenReturn(new ServiceNoticeResponse(false, "", null));
        when(devices.getAllDevices(any())).thenReturn(List.of());
        try (var accounts = mockStatic(ViewAuthUtils.class)) {
            accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(account);
            var layout = new MainLayout(auth, i18n, notices, devices, mock(com.nitramite.porssiohjain.services.FeatureRequestService.class));
            Div list = navigate(layout, "controls");
            Component frame = frames(layout).getFirst();
            frame.getElement().getClassList().remove("retro-window-maximized");
            Div details = navigate(layout, "controls/42");
            assertEquals(List.of(frame), frames(layout));
            assertFalse(frame.getElement().getClassList().contains("retro-window-maximized"));
            assertNull(list.getElement().getParent());
            assertTrue(elements(frame.getElement()).anyMatch(e -> e.equals(details.getElement())));
            assertEquals(1, elements(layout.getElement()).filter(e -> e.getClassList().contains("retro-task-button")).count());
            assertTrue(elements(frame.getElement()).anyMatch(e -> "icons/desktop/settings.svg".equals(e.getAttribute("src"))));
            Element task = elements(layout.getElement()).filter(e -> e.getClassList().contains("retro-task-button")).findFirst().orElseThrow();
            assertTrue(elements(task).anyMatch(e -> "icons/desktop/settings.svg".equals(e.getAttribute("src"))));
            navigate(layout, "device");
            Div returnedList = navigate(layout, "controls");
            assertEquals(2, frames(layout).size());
            assertSame(frame, frames(layout).getFirst());
            assertNull(details.getElement().getParent());
            assertTrue(elements(frame.getElement()).anyMatch(e -> e.equals(returnedList.getElement())));
        } finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void listAndDetailAliasesShareWindowKeys() {
        assertEquals(MainLayout.windowKey("production-sources"), MainLayout.windowKey("production-source/2"));
        assertEquals(MainLayout.windowKey("power-limits"), MainLayout.windowKey("power-limit/3"));
        assertEquals(MainLayout.windowKey("weather-controls"), MainLayout.windowKey("weather-controls/4"));
        assertEquals(MainLayout.windowKey("admin"), MainLayout.windowKey("admin/client-call-monitor"));
    }

    private Div navigate(MainLayout layout, String path) {
        Div content = new Div();
        layout.showRouterLayoutContent(content);
        var event = mock(AfterNavigationEvent.class);
        when(event.getLocation()).thenReturn(new Location(path));
        layout.afterNavigation(event);
        return content;
    }

    private List<Component> frames(MainLayout layout) {
        return layout.getChildren().filter(c -> c.getElement().getClassList().contains("retro-window")).toList();
    }

    private Stream<Element> elements(Element root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::elements));
    }
}
