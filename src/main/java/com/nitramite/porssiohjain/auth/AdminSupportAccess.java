/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.auth;

import com.nitramite.porssiohjain.services.AuthService;
import com.vaadin.flow.server.VaadinSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** Verifies support access to one selected account without changing its demo or admin flags. */
@Component
@RequiredArgsConstructor
public class AdminSupportAccess {
    public static final String IMPERSONATED_ACCOUNT_ID_ATTRIBUTE = "impersonatedAccountId";
    public static final String IMPERSONATING_ADMIN_ACCOUNT_ID_ATTRIBUTE = "impersonatingAdminAccountId";

    private static final ThreadLocal<SessionIdentity> backgroundIdentity = new ThreadLocal<>();

    private final AuthContext authContext;
    private final AuthService authService;

    public boolean isViewingAccount(Long accountId) {
        if (accountId == null) return false;
        if (authContext.getAccountId() != null) {
            // AuthInterceptor has already authenticated the real admin for this request.
            return authContext.isImpersonating() && accountId.equals(authContext.getAccountId());
        }
        return isViewingAccountInSession(authService, accountId);
    }

    public static boolean isViewingAccountInSession(AuthService authService, Long accountId) {
        SessionIdentity identity = currentIdentity();
        if (identity == null || accountId == null || !accountId.equals(identity.accountId())
                || identity.adminAccountId() == null || identity.token() == null || identity.token().isBlank()) {
            return false;
        }
        try {
            var admin = authService.authenticate(identity.token());
            return admin.isAdmin() && !admin.isDemo()
                    && identity.adminAccountId().equals(admin.getId()) && !accountId.equals(admin.getId())
                    && !authService.getAccount(accountId).isAdmin();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Carries identity, not cached authorization, into Vaadin's background cloud operations. */
    public static <T> Supplier<T> withCurrentSession(Supplier<T> work) {
        SessionIdentity identity = currentIdentity();
        return () -> {
            SessionIdentity previous = backgroundIdentity.get();
            if (identity == null) backgroundIdentity.remove();
            else backgroundIdentity.set(identity);
            try {
                return work.get();
            } finally {
                if (previous == null) backgroundIdentity.remove();
                else backgroundIdentity.set(previous);
            }
        };
    }

    private static SessionIdentity currentIdentity() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) return backgroundIdentity.get();
        Object token = session.getAttribute("token");
        Object adminId = session.getAttribute(IMPERSONATING_ADMIN_ACCOUNT_ID_ATTRIBUTE);
        Object targetId = session.getAttribute(IMPERSONATED_ACCOUNT_ID_ATTRIBUTE);
        return token instanceof String value && adminId instanceof Long admin && targetId instanceof Long target
                ? new SessionIdentity(value, admin, target) : null;
    }

    private record SessionIdentity(String token, Long adminAccountId, Long accountId) {}
}
