/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.ServiceNoticeService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.VaadinSession;
import java.util.Locale;

/** Shared Windows 98 desktop shell for authenticated routes. */
public class MainLayout extends Div implements RouterLayout, AfterNavigationObserver {
    private final I18nService i18n;
    private final Div window = new Div();
    private final Div windowBody = new Div();
    private final Div startMenu = new Div();
    private final Span windowTitle = new Span();
    private final Button taskButton = new Button();
    private boolean minimized;

    public MainLayout(AuthService authService, I18nService i18n, ServiceNoticeService serviceNoticeService) {
        this.i18n = i18n;
        addClassName("retro-desktop");
        Div icons = new Div();
        icons.addClassName("retro-icons");
        shortcut(icons, "home.myDevices", VaadinIcon.DESKTOP, DeviceView.class);
        shortcut(icons, "home.myControls", VaadinIcon.SLIDERS, ControlsView.class);
        shortcut(icons, "home.weatherControls", VaadinIcon.CLOUD, WeatherControlsView.class);
        shortcut(icons, "home.heatingPlanner", VaadinIcon.FIRE, HeatingPlannerView.class);
        shortcut(icons, "home.dashboard", VaadinIcon.DASHBOARD, DashboardView.class);
        shortcut(icons, "home.settings", VaadinIcon.COG, SettingsView.class);
        add(icons);
        var notice = serviceNoticeService.getNotice(UI.getCurrent().getLocale());
        if (notice.active()) {
            Div noticePanel = new Div();
            noticePanel.addClassName("retro-desktop-notice");
            noticePanel.add(new Span(t("home.serviceNotice")), new Span(notice.text()));
            add(noticePanel);
        }

        window.addClassName("retro-window");
        Div titleBar = new Div();
        titleBar.addClassName("retro-titlebar");
        titleBar.add(VaadinIcon.DESKTOP.create(), windowTitle);
        Div controls = new Div();
        controls.addClassName("retro-window-controls");
        Button minimize = chrome("_", "desktop.minimize", () -> {
            minimized = true;
            window.setVisible(false);
            taskButton.addClassName("retro-task-minimized");
        });
        Button maximize = chrome("□", "desktop.maximize", () -> {
            if (window.getClassNames().contains("retro-window-maximized")) {
                window.removeClassName("retro-window-maximized");
            } else {
                window.addClassName("retro-window-maximized");
            }
        });
        Button close = chrome("×", "desktop.close", () -> UI.getCurrent().navigate(DesktopView.class));
        controls.add(minimize, maximize, close);
        titleBar.add(controls);
        windowBody.addClassName("retro-window-body");
        window.add(titleBar, windowBody);
        add(window);

        Div taskbar = new Div();
        taskbar.addClassName("retro-taskbar");
        Button startButton = new Button(t("desktop.start"), VaadinIcon.MENU.create());
        startButton.addClassName("retro-start-button");
        startButton.getElement().setAttribute("aria-haspopup", "menu");
        startButton.getElement().setAttribute("aria-expanded", "false");
        startButton.addClickListener(e -> {
            boolean open = !startMenu.isVisible();
            startMenu.setVisible(open);
            startButton.getElement().setAttribute("aria-expanded", String.valueOf(open));
        });
        taskButton.addClassName("retro-task-button");
        taskButton.addClickListener(e -> {
            if (minimized) {
                minimized = false;
                window.setVisible(true);
                taskButton.removeClassName("retro-task-minimized");
            } else {
                UI.getCurrent().navigate(DesktopView.class);
            }
        });
        Span clock = new Span();
        clock.addClassName("retro-clock");
        clock.getElement().executeJs("const update=()=>{this.textContent=new Intl.DateTimeFormat(undefined,{hour:'2-digit',minute:'2-digit'}).format(new Date())};update();const timer=setInterval(update,30000);this.addEventListener('disconnected',()=>clearInterval(timer),{once:true})");
        taskbar.add(startButton, taskButton, clock);

        startMenu.addClassName("retro-start-menu");
        startMenu.getElement().setAttribute("role", "menu");
        menu("home.myDevices", VaadinIcon.DESKTOP, DeviceView.class);
        menu("home.myControls", VaadinIcon.SLIDERS, ControlsView.class);
        menu("home.weatherControls", VaadinIcon.CLOUD, WeatherControlsView.class);
        menu("home.heatingPlanner", VaadinIcon.FIRE, HeatingPlannerView.class);
        menu("home.loadShedding", VaadinIcon.WARNING, LoadSheddingView.class);
        menu("home.powerplant", VaadinIcon.DASHBOARD, PowerplantView.class);
        menu("home.solarAnglePlanner", VaadinIcon.SUN_O, SolarAnglePlannerView.class);
        menu("home.myProduction", VaadinIcon.LIGHTBULB, ProductionSourcesView.class);
        menu("home.powerLimits", VaadinIcon.FLASH, PowerLimitsView.class);
        menu("home.dashboard", VaadinIcon.CHART, DashboardView.class);
        menu("home.settings", VaadinIcon.COG, SettingsView.class);
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account != null && account.isAdmin()) {
            menu("home.admin", VaadinIcon.SHIELD, AdminView.class);
        }
        if (ViewAuthUtils.isImpersonating()) {
            action("home.stopImpersonating", VaadinIcon.CLOSE_CIRCLE, () -> {
                ViewAuthUtils.stopImpersonating();
                UI.getCurrent().navigate(DesktopView.class);
                UI.getCurrent().getPage().reload();
            });
        }
        Div separator = new Div();
        separator.addClassName("retro-menu-separator");
        startMenu.add(separator);
        action("lang.english", VaadinIcon.GLOBE, () -> changeLocale("en"));
        action("lang.finnish", VaadinIcon.GLOBE, () -> changeLocale("fi"));
        action("home.logout", VaadinIcon.SIGN_OUT, () -> {
            ViewAuthUtils.stopImpersonating();
            VaadinSession.getCurrent().setAttribute("token", null);
            VaadinSession.getCurrent().setAttribute("expiresAt", null);
            UI.getCurrent().navigate(HomeView.class);
        });
        startMenu.setVisible(false);
        add(startMenu, taskbar);
    }

    private void shortcut(Div icons, String key, VaadinIcon icon, Class<? extends Component> destination) {
        Button button = new Button(t(key), icon.create(), e -> UI.getCurrent().navigate(destination));
        button.addClassName("retro-shortcut");
        icons.add(button);
    }

    private void menu(String key, VaadinIcon icon, Class<? extends Component> destination) {
        action(key, icon, () -> UI.getCurrent().navigate(destination));
    }

    private void action(String key, VaadinIcon icon, Runnable runnable) {
        Button button = new Button(t(key), icon.create(), e -> {
            startMenu.setVisible(false);
            runnable.run();
        });
        button.addClassName("retro-menu-item");
        button.getElement().setAttribute("role", "menuitem");
        startMenu.add(button);
    }

    private Button chrome(String label, String key, Runnable runnable) {
        Button button = new Button(label, e -> runnable.run());
        button.addClassName("retro-chrome-button");
        button.getElement().setAttribute("aria-label", t(key));
        return button;
    }

    private void changeLocale(String language) {
        Locale locale = Locale.of(language, language.equals("fi") ? "FI" : "US");
        VaadinSession.getCurrent().setAttribute(Locale.class, locale);
        UI.getCurrent().setLocale(locale);
        UI.getCurrent().getPage().reload();
    }

    private String t(String key) {
        return i18n.t(key);
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        windowBody.getElement().appendChild(content.getElement());
    }

    @Override
    public void removeRouterLayoutContent(HasElement content) {
        content.getElement().removeFromParent();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        String path = event.getLocation().getPath();
        boolean desktop = path.equals("desktop");
        if (!desktop) {
            minimized = false;
            taskButton.removeClassName("retro-task-minimized");
        }
        window.setVisible(!desktop && !minimized);
        taskButton.setVisible(!desktop);
        String title = titleFor(path);
        windowTitle.setText(title);
        taskButton.setText(title);
        startMenu.setVisible(false);
    }

    private String titleFor(String path) {
        if (path.startsWith("admin")) return t("home.admin");
        if (path.startsWith("weather-controls")) return t("home.weatherControls");
        if (path.startsWith("heating-planner")) return t("home.heatingPlanner");
        if (path.startsWith("load-shedding")) return t("home.loadShedding");
        if (path.startsWith("powerplant")) return t("home.powerplant");
        if (path.startsWith("solar-angle-planner")) return t("home.solarAnglePlanner");
        if (path.startsWith("production-source")) return t("home.myProduction");
        if (path.startsWith("power-limit")) return t("home.powerLimits");
        if (path.startsWith("dashboard")) return t("home.dashboard");
        if (path.startsWith("settings")) return t("home.settings");
        if (path.startsWith("device")) return t("home.myDevices");
        if (path.startsWith("controls")) return t("home.myControls");
        if (path.startsWith("sites")) return t("desktop.sites");
        if (path.startsWith("electricity-contracts")) return t("desktop.contracts");
        if (path.startsWith("resource-sharing")) return t("desktop.resources");
        return t("home.subtitle");
    }
}
