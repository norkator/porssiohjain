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

    private ViewAuthUtils() {
    }

    public static AccountEntity getAuthenticatedAccount(AuthService authService, String sessionExpiredMessage) {
        VaadinSession session = VaadinSession.getCurrent();
        String token = session != null ? (String) session.getAttribute("token") : null;
        if (token == null || token.isBlank()) {
            clearSession(session);
            redirectToLogin(sessionExpiredMessage);
            return null;
        }

        try {
            return authService.authenticate(token);
        } catch (IllegalArgumentException e) {
            clearSession(session);
            redirectToLogin(sessionExpiredMessage);
            return null;
        }
    }

    public static boolean rerouteToLoginIfUnauthenticated(BeforeEnterEvent event, AuthService authService) {
        VaadinSession session = VaadinSession.getCurrent();
        String token = session != null ? (String) session.getAttribute("token") : null;
        if (token == null || token.isBlank()) {
            clearSession(session);
            event.forwardTo(LoginView.class);
            return true;
        }

        try {
            authService.authenticate(token);
            return false;
        } catch (IllegalArgumentException e) {
            clearSession(session);
            event.forwardTo(LoginView.class);
            return true;
        }
    }

    public static boolean hasValidSession(AuthService authService) {
        VaadinSession session = VaadinSession.getCurrent();
        String token = session != null ? (String) session.getAttribute("token") : null;
        if (token == null || token.isBlank()) {
            clearSession(session);
            return false;
        }

        try {
            authService.authenticate(token);
            return true;
        } catch (IllegalArgumentException e) {
            clearSession(session);
            return false;
        }
    }

    private static void clearSession(VaadinSession session) {
        if (session == null) {
            return;
        }
        session.setAttribute("token", null);
        session.setAttribute("expiresAt", null);
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
