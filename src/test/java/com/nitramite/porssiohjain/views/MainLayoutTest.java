package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
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
import java.util.concurrent.atomic.AtomicReference;
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
        ui = spy(new UI());
        var page = mock(com.vaadin.flow.component.page.Page.class);
        when(page.getHistory()).thenReturn(mock(com.vaadin.flow.component.page.History.class));
        doReturn(page).when(ui).getPage();
        UI.setCurrent(ui);
        var notices = mock(ServiceNoticeService.class);
        when(notices.getNotice(any())).thenReturn(new ServiceNoticeResponse(false, "", null));
        var translations = mock(I18nService.class, invocation -> invocation.getMethod().getName().equals("t")
                ? invocation.getArgument(0) : null);
        layout = new MainLayout(mock(AuthService.class), translations, notices, mock(DeviceService.class));
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
    void sameRouteReplacesContentAndRetainsFrameAndTask() {
        Div old = new Div();
        navigate("device", old);
        Component oldFrame = windows().getFirst();
        layout.removeRouterLayoutContent(old);
        navigate("device", new Div());
        assertEquals(1, windows().size());
        assertSame(oldFrame, windows().getFirst());
        assertTrue(old.getParent().isEmpty());
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
    void switchingToUserPreviewDiscardsRetainedAdminWindow() {
        var effective = new AtomicReference<>(AccountEntity.builder().admin(true).build());
        try (var accounts = mockStatic(ViewAuthUtils.class)) {
            accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(any(AuthService.class)))
                    .thenAnswer(invocation -> effective.get());
            navigate("admin/users", new Div("user listing"));
            navigate("device", new Div("device state"));
            assertEquals(2, windows().size());

            effective.set(AccountEntity.builder().admin(false).build());
            navigate("desktop", new Div());

            assertEquals(1, windows().size());
            assertEquals(1, tasks().size());
            assertFalse(layout.getElement().getTextRecursively().contains("user listing"));
            assertTrue(layout.getElement().getTextRecursively().contains("device state"));
        }
    }

    @Test
    void desktopContainsEveryStartFeatureAndLogout() {
        Component icons = layout.getChildren().flatMap(Component::getChildren)
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
            MainLayout finnish = new MainLayout(mock(AuthService.class), new I18nService(messages), notices, mock(DeviceService.class));
            String text = finnish.getElement().getTextRecursively();
            assertTrue(text.contains("Käynnistä"));
            assertTrue(text.contains("Omat laitteet"));
            assertTrue(text.contains("Kirjaudu ulos"));
            assertEquals(Locale.of("fi", "FI"), ui.getLocale());
        } finally {
            VaadinSession.setCurrent(null);
        }
    }

    @Test
    void backButtonUsesFeatureParentAndIsDisabledOnList() {
        navigate("controls/42", new Div());
        Button back = windows().getFirst().getChildren().flatMap(Component::getChildren)
                .filter(c -> c instanceof Button button && button.getText().contains("desktop.back"))
                .map(Button.class::cast).findFirst().orElseThrow();
        assertTrue(back.isEnabled());
        assertEquals("controls", MainLayout.parentRoute("controls/42?tab=settings"));
        assertEquals("production-sources", MainLayout.parentRoute("production-source/2"));
        navigate("controls", new Div());
        assertFalse(back.isEnabled());
    }

    @Test
    void switchingWindowUpdatesUrlWithoutReplacingItsDraft() {
        Div draft = new Div("draft");
        navigate("controls/42?tab=settings", draft);
        Button task = tasks().getFirst();
        navigate("device", new Div());
        UI current = mock(UI.class);
        var page = mock(com.vaadin.flow.component.page.Page.class);
        var history = mock(com.vaadin.flow.component.page.History.class);
        when(current.getPage()).thenReturn(page);
        when(page.getHistory()).thenReturn(history);
        UI.setCurrent(current);
        task.click();
        verify(history).replaceState(isNull(), eq("controls/42?tab=settings"));
        assertTrue(draft.getParent().isPresent());
        assertEquals("draft", draft.getText());
    }

    @Test
    void navigationOnlyGuardsTheWindowWhoseContentWillBeReplaced() {
        var field = new DesktopFormStateTest.ClientTextField();
        DesktopFormState.watch(field);
        navigate("controls/42", new Div(field));
        DesktopFormStateTest.edit(field, "draft");
        var other = mock(com.vaadin.flow.router.BeforeLeaveEvent.class);
        when(other.getLocation()).thenReturn(new Location("device"));
        doReturn(DeviceView.class).when(other).getNavigationTarget();
        layout.beforeLeave(other);
        verify(other, never()).postpone();

        var back = mock(com.vaadin.flow.router.BeforeLeaveEvent.class);
        when(back.getLocation()).thenReturn(new Location("controls"));
        doReturn(ControlsView.class).when(back).getNavigationTarget();
        var continuation = mock(com.vaadin.flow.router.BeforeLeaveEvent.ContinueNavigationAction.class);
        when(back.postpone()).thenReturn(continuation);
        layout.beforeLeave(back);
        verify(back).postpone();
        var dialog = layout.getChildren().filter(c -> c instanceof com.vaadin.flow.component.dialog.Dialog)
                .map(com.vaadin.flow.component.dialog.Dialog.class::cast).findFirst().orElseThrow();
        dialog.getFooter().getElement().getChildren().flatMap(element -> element.getComponent().stream()).filter(c -> c instanceof Button button && button.getText().equals("common.cancel"))
                .map(Button.class::cast).findFirst().orElseThrow().click();
        verify(continuation).cancel();
        assertTrue(DesktopFormState.isDirty(field));
        layout.beforeLeave(back);
        var discard = layout.getChildren().filter(c -> c instanceof com.vaadin.flow.component.dialog.Dialog)
                .map(com.vaadin.flow.component.dialog.Dialog.class::cast).findFirst().orElseThrow();
        discard.getFooter().getElement().getChildren().flatMap(element -> element.getComponent().stream())
                .filter(c -> c instanceof Button button && button.getText().equals("desktop.discard"))
                .map(Button.class::cast).findFirst().orElseThrow().click();
        verify(continuation).proceed();
        assertFalse(DesktopFormState.isDirty(field));
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
