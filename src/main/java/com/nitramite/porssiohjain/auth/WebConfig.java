/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private static final String[] DEFAULT_ALLOWED_ORIGINS = {
            "https://app.porssiohjain.fi",
            "https://app.energiaohjain.fi",
            "https://www.porssiohjain.fi",
            "http://localhost:5173"
    };

    private final AuthInterceptor authInterceptor;
    @Value("${app.cors.allow-all:false}")
    private boolean allowAllCorsOrigins;

    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(
            CorsRegistry registry
    ) {
        var registration = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);

        if (allowAllCorsOrigins) {
            registration.allowedOriginPatterns("*");
            return;
        }

        registration.allowedOrigins(DEFAULT_ALLOWED_ORIGINS);
    }
}
