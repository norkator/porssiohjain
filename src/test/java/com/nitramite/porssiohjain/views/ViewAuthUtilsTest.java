package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.AdminAccountService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.SystemLogService;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ViewAuthUtilsTest {

    @AfterEach
    void cleanup() {
        VaadinSession.setCurrent(null);
    }

    @Test
    void adminRouteChecksEffectiveAccountDuringUserPreview() {
        var session = mock(VaadinSession.class);
        VaadinSession.setCurrent(session);
        when(session.getAttribute("token")).thenReturn("admin-token");
        when(session.getAttribute("impersonatedAccountId")).thenReturn(2L);
        when(session.getAttribute("impersonatingAdminAccountId")).thenReturn(1L);
        var auth = mock(AuthService.class);
        when(auth.authenticate("admin-token")).thenReturn(AccountEntity.builder().id(1L).admin(true).build());
        when(auth.getAccount(2L)).thenReturn(AccountEntity.builder().id(2L).admin(false).build());
        var event = mock(BeforeEnterEvent.class);

        assertTrue(ViewAuthUtils.rerouteToHomeIfNotAdmin(event, auth));
        verify(event).forwardTo(DesktopView.class);
        assertUsersViewDoesNotQueryAccounts(auth);
    }

    @Test
    void ordinaryUserCannotOpenAdminRoute() {
        var session = mock(VaadinSession.class);
        VaadinSession.setCurrent(session);
        when(session.getAttribute("token")).thenReturn("user-token");
        var auth = mock(AuthService.class);
        when(auth.authenticate("user-token")).thenReturn(AccountEntity.builder().id(2L).admin(false).build());
        var event = mock(BeforeEnterEvent.class);

        assertTrue(ViewAuthUtils.rerouteToHomeIfNotAdmin(event, auth));
        verify(event).forwardTo(DesktopView.class);
        assertUsersViewDoesNotQueryAccounts(auth);
    }

    @Test
    void adminCanOpenAdminRouteOutsidePreview() {
        var session = mock(VaadinSession.class);
        VaadinSession.setCurrent(session);
        when(session.getAttribute("token")).thenReturn("admin-token");
        var auth = mock(AuthService.class);
        when(auth.authenticate("admin-token")).thenReturn(AccountEntity.builder().id(1L).admin(true).build());
        var event = mock(BeforeEnterEvent.class);

        assertFalse(ViewAuthUtils.rerouteToHomeIfNotAdmin(event, auth));
        verifyNoInteractions(event);
    }

    private void assertUsersViewDoesNotQueryAccounts(AuthService auth) {
        var accounts = mock(AccountRepository.class);
        new AdminUsersView(auth, mock(I18nService.class), accounts,
                mock(AccountLimitService.class), mock(AdminAccountService.class), mock(SystemLogService.class));
        verifyNoInteractions(accounts);
    }
}
