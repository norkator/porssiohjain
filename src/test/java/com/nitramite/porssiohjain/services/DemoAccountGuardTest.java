package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.auth.AdminSupportAccess;
import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.views.ViewAuthUtils;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoAccountGuardTest {
    private final AccountRepository accounts = mock();
    private final AuthService auth = mock();
    private final AuthContext context = new AuthContext();
    private final DemoAccountGuard guard = new DemoAccountGuard(accounts, new AdminSupportAccess(context, auth));
    private final AccountEntity admin = AccountEntity.builder().id(1L).admin(true).build();
    private final AccountEntity demo = AccountEntity.builder().id(2L).demo(true).build();
    private final Map<String, Object> attributes = new HashMap<>();

    @BeforeEach
    void setup() {
        context.clear();
        when(accounts.findById(2L)).thenReturn(Optional.of(demo));
        when(auth.authenticate("admin-token")).thenReturn(admin);
        when(auth.getAccount(2L)).thenReturn(demo);
    }

    @AfterEach
    void cleanup() {
        context.clear();
        VaadinSession.setCurrent(null);
    }

    @Test
    void demoWritesRequireSupportAccessEvenWhenTheCallerIsAnAdmin() {
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
        context.setAccount(2L, true);
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
        context.setAdminAccount(true);
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
        assertFalse(ViewAuthUtils.canEditAccount(auth, demo));
    }

    @Test
    void apiSupportIsLimitedToTheSelectedAccountAndClearedAfterTheRequest() {
        context.setAccount(2L, true);
        context.setImpersonating(true);
        assertDoesNotThrow(() -> guard.assertWritable(2L));
        var otherDemo = AccountEntity.builder().id(3L).demo(true).build();
        when(accounts.findById(3L)).thenReturn(Optional.of(otherDemo));
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(3L));
        context.clear();
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
        assertTrue(demo.isDemo());
        assertFalse(demo.isAdmin());
    }

    @Test
    void vaadinSupportRevalidatesTheRealAdminAndStopsWhenPreviewEnds() {
        previewSession();
        assertTrue(ViewAuthUtils.canEditAccount(auth, demo));
        assertDoesNotThrow(() -> guard.assertWritable(2L));
        admin.setAdmin(false);
        assertFalse(ViewAuthUtils.canEditAccount(auth, demo));
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
        admin.setAdmin(true);
        ViewAuthUtils.stopImpersonating();
        assertFalse(ViewAuthUtils.canEditAccount(auth, demo));
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
    }

    @Test
    void vaadinRejectsForgedSessionIdentityAndExpiredAuthentication() {
        previewSession();
        attributes.put("impersonatingAdminAccountId", 99L);
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
        attributes.put("impersonatingAdminAccountId", 1L);
        when(auth.authenticate("admin-token")).thenThrow(new IllegalArgumentException("Token expired"));
        assertFalse(ViewAuthUtils.canEditAccount(auth, demo));
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
    }

    @Test
    void aDemoSessionCannotGrantItselfSupportAccess() {
        previewSession();
        admin.setDemo(true);
        assertFalse(ViewAuthUtils.canEditAccount(auth, demo));
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
    }

    @Test
    void apiIdentityCannotFallBackToAnUnrelatedVaadinSupportSession() {
        previewSession();
        context.setAccount(2L, true);
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
    }

    @Test
    void backgroundCloudWorkCarriesIdentityRechecksAuthorizationAndDoesNotLeakAccess() {
        previewSession();
        var work = AdminSupportAccess.withCurrentSession(() -> {
            guard.assertWritable(2L);
            return true;
        });
        VaadinSession.setCurrent(null);
        assertTrue(CompletableFuture.supplyAsync(work).join());
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
        admin.setAdmin(false);
        assertThrows(DemoAccountMutationException.class, work::get);
        assertThrows(DemoAccountMutationException.class, () -> guard.assertWritable(2L));
    }

    private void previewSession() {
        var session = mock(VaadinSession.class);
        when(session.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
        doAnswer(call -> {
            attributes.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), any());
        attributes.put("token", "admin-token");
        attributes.put("impersonatingAdminAccountId", 1L);
        attributes.put("impersonatedAccountId", 2L);
        VaadinSession.setCurrent(session);
    }
}
