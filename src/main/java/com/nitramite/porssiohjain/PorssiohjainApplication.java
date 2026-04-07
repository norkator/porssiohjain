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

package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.services.SystemLogService;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.component.page.Push;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.component.page.AppShellConfigurator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Theme("my-theme")
@Push
public class PorssiohjainApplication implements AppShellConfigurator {

    @Autowired
    SystemLogService systemLogService;

    public static void main(String[] args) {
        SpringApplication.run(PorssiohjainApplication.class, args);
    }

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addFavIcon("icon", "favicon.ico", "any");
        settings.addLink("apple-touch-icon", "favicon.png");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        systemLogService.log("Application started successfully.");
    }

}
