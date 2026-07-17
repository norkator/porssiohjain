/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.VaadinSession;

public final class ViewAuthUtils {

    private static final String TOKEN_ATTRIBUTE = "token";
    private static final String EXPIRES_AT_ATTRIBUTE = "expiresAt";
    private static final String IMPERSONATED_ACCOUNT_ID_ATTRIBUTE = "impersonatedAccountId";
    private static final String IMPERSONATING_ADMIN_ACCOUNT_ID_ATTRIBUTE = "impersonatingAdminAccountId";

    private ViewAuthUtils() {
    }

    public static AccountEntity getAuthenticatedAccount(AuthService authService, String sessionExpiredMessage) {
        VaadinSession session = VaadinSession.getCurrent();
        String token = session != null ? (String) session.getAttribute(TOKEN_ATTRIBUTE) : null;
        if (token == null || token.isBlank()) {
            clearSession(session);
            redirectToLogin(sessionExpiredMessage);
            return null;
        }

        try {
            return resolveEffectiveAccount(authService, session, token);
        } catch (IllegalArgumentException e) {
            clearSession(session);
            redirectToLogin(sessionExpiredMessage);
            return null;
        }
    }

    public static AccountEntity findAuthenticatedAccount(AuthService authService) {
        VaadinSession session = VaadinSession.getCurrent();
        String token = session != null ? (String) session.getAttribute(TOKEN_ATTRIBUTE) : null;
        if (token == null || token.isBlank()) {
            clearSession(session);
            return null;
        }

        try {
            return resolveEffectiveAccount(authService, session, token);
        } catch (IllegalArgumentException e) {
            clearSession(session);
            return null;
        }
    }

    public static AccountEntity findRealAuthenticatedAccount(AuthService authService) {
        VaadinSession session = VaadinSession.getCurrent();
        String token = session != null ? (String) session.getAttribute(TOKEN_ATTRIBUTE) : null;
        if (token == null || token.isBlank()) {
            clearSession(session);
            return null;
        }

        try {
            return authService.authenticate(token);
        } catch (IllegalArgumentException e) {
            clearSession(session);
            return null;
        }
    }

    public static boolean rerouteToLoginIfUnauthenticated(BeforeEnterEvent event, AuthService authService) {
        VaadinSession session = VaadinSession.getCurrent();
        String token = session != null ? (String) session.getAttribute(TOKEN_ATTRIBUTE) : null;
        if (token == null || token.isBlank()) {
            clearSession(session);
            event.forwardTo(LoginView.class);
            return true;
        }

        try {
            resolveEffectiveAccount(authService, session, token);
            return false;
        } catch (IllegalArgumentException e) {
            clearSession(session);
            event.forwardTo(LoginView.class);
            return true;
        }
    }

    public static boolean hasValidSession(AuthService authService) {
        return findAuthenticatedAccount(authService) != null;
    }

    public static boolean rerouteToHomeIfNotAdmin(BeforeEnterEvent event, AuthService authService) {
        AccountEntity account = findRealAuthenticatedAccount(authService);
        if (account == null) {
            event.forwardTo(LoginView.class);
            return true;
        }
        if (!account.isAdmin()) {
            event.forwardTo(HomeView.class);
            return true;
        }
        return false;
    }

    public static void startImpersonating(AuthService authService, Long targetAccountId) {
        AccountEntity admin = findRealAuthenticatedAccount(authService);
        if (admin == null || !admin.isAdmin()) {
            throw new IllegalArgumentException("Admin access required");
        }

        AccountEntity target = authService.getAccount(targetAccountId);
        if (admin.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Cannot impersonate your own account");
        }
        if (target.isAdmin()) {
            throw new IllegalArgumentException("Cannot impersonate an admin account");
        }

        VaadinSession session = VaadinSession.getCurrent();
        session.setAttribute(IMPERSONATING_ADMIN_ACCOUNT_ID_ATTRIBUTE, admin.getId());
        session.setAttribute(IMPERSONATED_ACCOUNT_ID_ATTRIBUTE, target.getId());
    }

    public static void stopImpersonating() {
        clearImpersonation(VaadinSession.getCurrent());
    }

    public static boolean isImpersonating() {
        VaadinSession session = VaadinSession.getCurrent();
        return session != null && session.getAttribute(IMPERSONATED_ACCOUNT_ID_ATTRIBUTE) instanceof Long;
    }

    public static Long getImpersonatedAccountId() {
        VaadinSession session = VaadinSession.getCurrent();
        Object accountId = session != null ? session.getAttribute(IMPERSONATED_ACCOUNT_ID_ATTRIBUTE) : null;
        return accountId instanceof Long ? (Long) accountId : null;
    }

    private static AccountEntity resolveEffectiveAccount(AuthService authService, VaadinSession session, String token) {
        AccountEntity realAccount = authService.authenticate(token);
        if (session == null) {
            return realAccount;
        }

        Object impersonatedAccountId = session.getAttribute(IMPERSONATED_ACCOUNT_ID_ATTRIBUTE);
        Object impersonatingAdminAccountId = session.getAttribute(IMPERSONATING_ADMIN_ACCOUNT_ID_ATTRIBUTE);
        if (!(impersonatedAccountId instanceof Long targetAccountId)) {
            clearImpersonation(session);
            return realAccount;
        }
        if (!(impersonatingAdminAccountId instanceof Long adminAccountId)
                || !adminAccountId.equals(realAccount.getId())
                || !realAccount.isAdmin()) {
            clearImpersonation(session);
            return realAccount;
        }

        AccountEntity targetAccount;
        try {
            targetAccount = authService.getAccount(targetAccountId);
        } catch (IllegalArgumentException e) {
            clearImpersonation(session);
            return realAccount;
        }
        if (targetAccount.isAdmin()) {
            clearImpersonation(session);
            return realAccount;
        }
        return targetAccount;
    }

    private static void clearSession(VaadinSession session) {
        if (session == null) {
            return;
        }
        clearImpersonation(session);
        session.setAttribute(TOKEN_ATTRIBUTE, null);
        session.setAttribute(EXPIRES_AT_ATTRIBUTE, null);
    }

    private static void clearImpersonation(VaadinSession session) {
        if (session == null) {
            return;
        }
        session.setAttribute(IMPERSONATED_ACCOUNT_ID_ATTRIBUTE, null);
        session.setAttribute(IMPERSONATING_ADMIN_ACCOUNT_ID_ATTRIBUTE, null);
    }

    private static void redirectToLogin(String sessionExpiredMessage) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            return;
        }

        Notification notification = Notification.show(sessionExpiredMessage);
        notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
        ui.navigate(LoginView.class);
    }
}
