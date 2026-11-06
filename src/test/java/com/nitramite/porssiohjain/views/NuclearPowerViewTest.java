package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NuclearPowerViewTest {
    @Test
    void consoleLoadsOnlyAfterAuthenticationAndKeepsItsEnglishSessionOnReentry() {
        var auth = mock(AuthService.class);
        var event = mock(BeforeEnterEvent.class);
        var view = new NuclearPowerView(auth);
        try (var guard = mockStatic(ViewAuthUtils.class)) {
            guard.when(() -> ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, auth)).thenReturn(true);
            view.beforeEnter(event);
            assertEquals(0, view.getElement().getChildCount());

            guard.when(() -> ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, auth)).thenReturn(false);
            view.beforeEnter(event);
            var console = view.getElement().getChild(0);
            assertEquals("nuclear-power-console", console.getTag());
            assertEquals("en", view.getElement().getAttribute("lang"));
            view.beforeEnter(event);
            assertEquals(1, view.getElement().getChildCount());
            assertSame(console.getNode(), view.getElement().getChild(0).getNode());
        }
        verifyNoInteractions(auth);
    }
}
