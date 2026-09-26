package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.ServiceNoticeService;
import com.nitramite.porssiohjain.services.models.ServiceNoticeResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MainLayoutTest {
    private MainLayout layout;
    private UI ui;

    @BeforeEach
    void setup() {
        ui = new UI();
        UI.setCurrent(ui);
        var notices = mock(ServiceNoticeService.class);
        when(notices.getNotice(any())).thenReturn(new ServiceNoticeResponse(false, "", null));
        var translations = mock(I18nService.class, invocation -> invocation.getMethod().getName().equals("t")
                ? invocation.getArgument(0) : null);
        layout = new MainLayout(mock(AuthService.class), translations, notices);
    }

    @AfterEach
    void cleanup() {
        UI.setCurrent(null);
    }

    @Test
    void navigationRetainsOtherWindowsAndTheirState() {
        Div devices = new Div("unsaved form state");
        navigate("device", devices);
        layout.removeRouterLayoutContent(devices);
        navigate("controls", new Div());
        assertEquals(2, windows().size());
        assertTrue(devices.getParent().isPresent());
        assertEquals("unsaved form state", devices.getText());
        navigate("desktop", new Div());
        assertEquals(2, windows().size());
    }

    @Test
    void sameRouteReplacesOldViewAndTask() {
        Div old = new Div();
        navigate("device", old);
        Component oldFrame = windows().getFirst();
        layout.removeRouterLayoutContent(old);
        navigate("device", new Div());
        assertEquals(1, windows().size());
        assertTrue(oldFrame.getParent().isEmpty());
        assertEquals(1, tasks().size());
    }

    @Test
    void taskbarMinimizesRestoresAndSwitchesWithoutRemovingViews() {
        navigate("device", new Div());
        Component first = windows().getFirst();
        Button task = tasks().getFirst();
        task.click();
        assertFalse(first.isVisible());
        task.click();
        assertTrue(first.isVisible());
        navigate("controls", new Div());
        task.click();
        assertTrue(first.isVisible());
        assertTrue(first.getElement().getClassList().contains("retro-window-active"));
        assertEquals(2, windows().size());
    }

    @Test
    void newWindowsStartMaximized() {
        navigate("device", new Div());
        assertTrue(windows().getFirst().getElement().getClassList().contains("retro-window-maximized"));
    }

    @Test
    void desktopContainsEveryStartFeatureAndLogout() {
        Component icons = layout.getChildren()
                .filter(c -> c.getElement().getClassList().contains("retro-icons")).findFirst().orElseThrow();
        List<String> labels = icons.getChildren().map(c -> c.getElement().getTextRecursively()).toList();
        assertEquals(List.of("home.myDevices", "home.myControls", "home.weatherControls",
                "home.heatingPlanner", "home.loadShedding", "home.powerplant", "home.solarAnglePlanner",
                "home.myProduction", "home.powerLimits", "home.dashboard", "home.settings", "desktop.googlePlay", "home.buyMeACoffee", "home.logout"), labels);
    }

    @Test
    void desktopRestoresFinnishSessionLocaleBeforeBuildingLabels() {
        VaadinSession session = mock(VaadinSession.class);
        when(session.getAttribute(Locale.class)).thenReturn(Locale.of("fi", "FI"));
        VaadinSession.setCurrent(session);
        try {
            ui.setLocale(Locale.US);
            var messages = new ResourceBundleMessageSource();
            messages.setBasename("translations/messages");
            messages.setDefaultEncoding("UTF-8");
            var notices = mock(ServiceNoticeService.class);
            when(notices.getNotice(any())).thenReturn(new ServiceNoticeResponse(false, "", null));
            MainLayout finnish = new MainLayout(mock(AuthService.class), new I18nService(messages), notices);
            String text = finnish.getElement().getTextRecursively();
            assertTrue(text.contains("Käynnistä"));
            assertTrue(text.contains("Omat laitteet"));
            assertTrue(text.contains("Kirjaudu ulos"));
            assertEquals(Locale.of("fi", "FI"), ui.getLocale());
        } finally {
            VaadinSession.setCurrent(null);
        }
    }

    private void navigate(String path, Div content) {
        layout.showRouterLayoutContent(content);
        AfterNavigationEvent event = mock(AfterNavigationEvent.class);
        when(event.getLocation()).thenReturn(new Location(path));
        layout.afterNavigation(event);
    }

    private List<Component> windows() {
        return layout.getChildren().filter(c -> c.getElement().getClassList().contains("retro-window")).toList();
    }

    private List<Button> tasks() {
        return layout.getChildren().flatMap(Component::getChildren).flatMap(Component::getChildren)
                .filter(c -> c.getElement().getClassList().contains("retro-task-button"))
                .map(Button.class::cast).toList();
    }
}
