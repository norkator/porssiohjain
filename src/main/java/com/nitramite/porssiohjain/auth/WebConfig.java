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
            "https://mobile.porssiohjain.fi",
            "https://app.energiaohjain.fi",
            "https://mobile.energiaohjain.fi",
            "https://www.porssiohjain.fi"
    };
    private static final String[] MOBILE_API_PATHS = {
            "/account/**",
            "/me/**",
            "/devices/**",
            "/api/**",
            "/onboarding/**",
            "/dashboard/**",
            "/nordpool/**",
            "/power/**",
            "/control/**",
            "/device/**"
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
        for (String path : MOBILE_API_PATHS) {
            registry.addMapping(path)
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowedOriginPatterns("*")
                    .allowCredentials(false);
        }

        if (allowAllCorsOrigins) {
            registry.addMapping("/**")
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowedOriginPatterns("*")
                    .allowCredentials(false);
            return;
        }

        registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .allowedOrigins(DEFAULT_ALLOWED_ORIGINS);
    }
}
