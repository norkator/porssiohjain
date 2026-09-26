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
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.VaadinSession;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Shared Windows 98 desktop shell for authenticated routes. */
@JsModule("./desktop-windows.js")
public class MainLayout extends Div implements RouterLayout, AfterNavigationObserver {
    private final I18nService i18n;
    private final Div startMenu = new Div();
    private final Div menuItems = new Div();
    private final Div tasks = new Div();
    private final Div routeHost = new Div();
    private final Map<String, DesktopWindow> windows = new LinkedHashMap<>();
    private final Button startButton = new Button();
    private HasElement pendingContent;
    private DesktopWindow activeWindow;

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

        routeHost.setVisible(false);
        add(routeHost);
        Div taskbar = new Div();
        taskbar.addClassName("retro-taskbar");
        startButton.setText(t("desktop.start"));
        startButton.setIcon(pixelIcon(VaadinIcon.MENU));
        startButton.addClassName("retro-start-button");
        startButton.getElement().setAttribute("aria-haspopup", "true");
        startButton.getElement().setAttribute("aria-expanded", "false");
        startButton.addClickListener(e -> setStartOpen(!startMenu.isVisible()));
        tasks.addClassName("retro-tasks");
        Span clock = new Span();
        clock.addClassName("retro-clock");
        clock.getElement().executeJs("const update=()=>{this.textContent=new Intl.DateTimeFormat(undefined,{hour:'2-digit',minute:'2-digit'}).format(new Date())};update();const timer=setInterval(update,30000);this.addEventListener('disconnected',()=>clearInterval(timer),{once:true})");
        taskbar.add(startButton, tasks, clock);

        startMenu.addClassName("retro-start-menu");
        Span brand = new Span("Pörssiohjain 98");
        brand.addClassName("retro-start-brand");
        menuItems.addClassName("retro-menu-items");
        startMenu.add(brand, menuItems);
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
        menuItems.add(separator);
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
        NativeButton button = new NativeButton();
        button.add(pixelIcon(icon), new Span(t(key)));
        button.addClickListener(e -> open(destination));
        button.addClassName("retro-shortcut");
        icons.add(button);
    }

    private void menu(String key, VaadinIcon icon, Class<? extends Component> destination) {
        action(key, icon, () -> open(destination));
    }

    private void action(String key, VaadinIcon icon, Runnable runnable) {
        Button button = new Button(t(key), pixelIcon(icon), e -> {
            setStartOpen(false);
            runnable.run();
        });
        button.addClassName("retro-menu-item");
        menuItems.add(button);
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

    private void setStartOpen(boolean open) {
        startMenu.setVisible(open);
        startButton.getElement().setAttribute("aria-expanded", String.valueOf(open));
    }

    private void open(Class<? extends Component> destination) {
        setStartOpen(false);
        windows.values().stream()
                .filter(w -> destination.isInstance(w.content)).findFirst()
                .ifPresentOrElse(this::activate, () -> UI.getCurrent().navigate(destination));
    }

    private Component pixelIcon(VaadinIcon icon) {
        String name = switch (icon) {
            case DESKTOP -> "computer";
            case SLIDERS, COG -> "settings";
            case CLOUD -> "weather";
            case FIRE, FLASH, SUN_O, LIGHTBULB -> "energy";
            case MENU -> "start";
            default -> "folder";
        };
        Image image = new Image("icons/desktop/" + name + ".svg", "");
        image.addClassName("retro-pixel-icon");
        return image;
    }

    private void activate(DesktopWindow target) {
        activeWindow = target;
        target.frame.setVisible(true);
        windows.values().forEach(w -> {
            w.frame.getElement().getClassList().set("retro-window-active", w == target);
            w.task.getElement().getClassList().set("retro-task-active", w == target);
        });
        target.frame.getElement().executeJs("this.dispatchEvent(new CustomEvent('desktop-raise'))");
    }

    private void close(DesktopWindow target) {
        windows.values().remove(target);
        remove(target.frame);
        tasks.remove(target.task);
        if (activeWindow == target) activeWindow = null;
        UI.getCurrent().navigate(DesktopView.class);
    }

    private final class DesktopWindow {
        final Div frame = new Div();
        final Button task;
        final HasElement content;

        DesktopWindow(String path, HasElement content) {
            this.content = content;
            String title = titleFor(path);
            frame.addClassName("retro-window");
            frame.getElement().setAttribute("role", "region");
            frame.getElement().setAttribute("aria-label", title);
            frame.getStyle().set("--window-offset", (windows.size() % 6 * 22) + "px");
            Div titleBar = new Div();
            titleBar.addClassName("retro-titlebar");
            titleBar.add(pixelIcon(VaadinIcon.DESKTOP), new Span(title));
            Div controls = new Div();
            controls.addClassName("retro-window-controls");
            controls.add(chrome("_", "desktop.minimize", this::minimize),
                    chrome("□", "desktop.maximize", this::maximize),
                    chrome("×", "desktop.close", () -> close(this)));
            titleBar.add(controls);
            MenuBar menus = new MenuBar();
            menus.addClassName("retro-window-menubar");
            menus.addItem(t("desktop.file")).getSubMenu()
                    .addItem(t("desktop.close"), e -> close(this));
            var view = menus.addItem(t("desktop.view")).getSubMenu();
            view.addItem(t("desktop.minimize"), e -> minimize());
            view.addItem(t("desktop.maximize"), e -> maximize());
            menus.addItem(t("desktop.help")).getSubMenu().addItem(t("desktop.about"), e -> {
                Dialog dialog = new Dialog();
                dialog.setHeaderTitle("Pörssiohjain 98");
                dialog.add(new Span(t("desktop.helpText")));
                dialog.getFooter().add(new Button(t("desktop.close"), click -> dialog.close()));
                dialog.open();
            });
            Div body = new Div();
            body.addClassName("retro-window-body");
            body.getElement().appendChild(content.getElement());
            frame.add(titleBar, menus, body);
            task = new Button(title, pixelIcon(VaadinIcon.DESKTOP), e -> {
                if (activeWindow == this && frame.isVisible()) minimize();
                else activate(this);
            });
            task.addClassName("retro-task-button");
            frame.getElement().addEventListener("pointerdown", e -> {
                if (activeWindow != this) activate(this);
            });
            frame.getElement().addEventListener("desktop-maximize", e -> maximize());
            frame.addAttachListener(e -> frame.getElement().executeJs("window.initDesktopWindow(this)"));
        }

        void minimize() {
            frame.setVisible(false);
            task.removeClassName("retro-task-active");
            if (activeWindow == this) activeWindow = null;
        }

        void maximize() {
            frame.getElement().getClassList().set("retro-window-maximized",
                    !frame.getClassNames().contains("retro-window-maximized"));
            activate(this);
        }
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        pendingContent = content;
        routeHost.getElement().appendChild(content.getElement());
    }

    @Override
    public void removeRouterLayoutContent(HasElement content) {
        // Open windows own their views until closed; retain their form and scroll state.
        if (content.getElement().getParent() == routeHost.getElement()) {
            content.getElement().removeFromParent();
        }
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        String path = event.getLocation().getPath();
        if (!path.equals("desktop") && pendingContent != null) {
            DesktopWindow previous = windows.remove(path);
            if (previous != null) {
                remove(previous.frame);
                tasks.remove(previous.task);
            }
            DesktopWindow next = new DesktopWindow(path, pendingContent);
            windows.put(path, next);
            add(next.frame);
            tasks.add(next.task);
            activate(next);
        }
        pendingContent = null;
        setStartOpen(false);
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
