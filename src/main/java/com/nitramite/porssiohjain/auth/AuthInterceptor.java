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

package com.nitramite.porssiohjain.auth;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String DEMO_WRITE_BLOCKED_MESSAGE = "Demo account is read-only.";

    private final AuthService authService;
    private final AuthContext authContext;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        if (method.getMethodAnnotation(RequireAuth.class) == null &&
                method.getBeanType().getAnnotation(RequireAuth.class) == null) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing Authorization header");
            return false;
        }

        try {
            AccountEntity account = authService.authenticate(token);
            authContext.setAccount(account.getId(), account.isDemo());
            if (account.isDemo() && isWriteRequest(request) && !isDemoWriteAllowed(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                response.getWriter().write(DEMO_WRITE_BLOCKED_MESSAGE);
                return false;
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized");
            return false;
        }

        return true;
    }

    private boolean isWriteRequest(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                || HttpMethod.PUT.matches(request.getMethod())
                || HttpMethod.PATCH.matches(request.getMethod())
                || HttpMethod.DELETE.matches(request.getMethod());
    }

    private boolean isDemoWriteAllowed(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/account/qr-login/")
                || request.getRequestURI().equals("/api/feedback");
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        authContext.clear();
    }

}
