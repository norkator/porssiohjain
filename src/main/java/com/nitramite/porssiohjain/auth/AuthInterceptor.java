package com.nitramite.porssiohjain.auth;

import com.nitramite.porssiohjain.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {

        // Only intercept controller methods
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        // Skip if method or class does not have @RequireAuth
        if (method.getMethodAnnotation(RequireAuth.class) == null &&
                method.getBeanType().getAnnotation(RequireAuth.class) == null) {
            return true;
        }

        // Check Authorization header
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing Authorization header");
            return false;
        }

        try {
            authService.authenticate(token);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(e.getMessage());
            return false;
        }

        return true;
    }
}