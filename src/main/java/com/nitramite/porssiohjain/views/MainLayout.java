/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.FeatureRequestService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.ServiceNoticeService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.BeforeLeaveObserver;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.server.VaadinSession;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Shared Windows 98 desktop shell for authenticated routes. */
@JsModule("./desktop-windows.js")
public class MainLayout extends Div implements RouterLayout, AfterNavigationObserver, BeforeLeaveObserver {
    private final AuthService authService;
    private final I18nService i18n;
    private final DesktopDeviceStatus deviceStatus;
    private final Div icons = new Div();
    private final Div startMenu = new Div();
    private final Div menuItems = new Div();
    private final Div tasks = new Div();
    private final Div routeHost = new Div();
    private final Map<String, DesktopWindow> windows = new LinkedHashMap<>();
    private final Map<String, VaadinIcon> featureIcons = new LinkedHashMap<>();
    private final Button startButton = new Button();
    private HasElement pendingContent;
    private DesktopWindow activeWindow;
    private FeatureRequestDialog featureRequestDialog;

    public MainLayout(AuthService authService, I18nService i18n, ServiceNoticeService serviceNoticeService, DeviceService deviceService, FeatureRequestService featureRequestService) {
        this.authService = authService;
        this.i18n = i18n;
        addClassName("retro-desktop");
        VaadinSession session = VaadinSession.getCurrent();
        Locale storedLocale = session != null ? session.getAttribute(Locale.class) : null;
        if (storedLocale != null) UI.getCurrent().setLocale(storedLocale);
        icons.addClassName("retro-icons");
        icons.addAttachListener(e -> icons.getElement().executeJs("window.initDesktopIcons(this)"));
        var notice = serviceNoticeService.getNotice(UI.getCurrent().getLocale());
        if (notice.active()) {
            add(serviceNotice(notice.text()));
        }
        deviceStatus = new DesktopDeviceStatus(authService, deviceService, i18n, () -> open(DeviceView.class));
        Div desktopContent = new Div(icons, deviceStatus);
        desktopContent.addClassName("retro-desktop-content");
        add(desktopContent);

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
        feature("home.myDevices", VaadinIcon.DESKTOP, DeviceView.class);
        feature("home.myControls", VaadinIcon.SLIDERS, ControlsView.class);
        feature("home.weatherControls", VaadinIcon.CLOUD, WeatherControlsView.class);
        feature("home.heatingPlanner", VaadinIcon.FIRE, HeatingPlannerView.class);
        feature("home.loadShedding", VaadinIcon.WARNING, LoadSheddingView.class);
        feature("home.powerplant", VaadinIcon.DASHBOARD, PowerplantView.class);
        feature("home.solarAnglePlanner", VaadinIcon.SUN_O, SolarAnglePlannerView.class);
        feature("home.myProduction", VaadinIcon.LIGHTBULB, ProductionSourcesView.class);
        feature("home.powerLimits", VaadinIcon.FLASH, PowerLimitsView.class);
        feature("home.dashboard", VaadinIcon.CHART, DashboardView.class);
        feature("home.settings", VaadinIcon.COG, SettingsView.class);
        Runnable feedback = () -> {
            setStartOpen(false);
            if (ViewAuthUtils.findAuthenticatedAccount(authService) == null) return;
            if (featureRequestDialog == null) {
                featureRequestDialog = new FeatureRequestDialog(authService, featureRequestService, i18n);
                add(featureRequestDialog);
            }
            featureRequestDialog.open();
        };
        shortcut("featureRequest.title", VaadinIcon.COMMENT, feedback);
        action("featureRequest.title", VaadinIcon.COMMENT, feedback);
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account != null && account.isAdmin()) {
            feature("home.admin", VaadinIcon.SHIELD, AdminView.class);
        }
        if (ViewAuthUtils.isImpersonating()) {
            action("home.stopImpersonating", VaadinIcon.CLOSE_CIRCLE, () ->
                    confirmDiscard(hasUnsavedChanges(), () -> {
                        windows.values().forEach(w -> DesktopFormState.saved(w.body));
                        if (featureRequestDialog != null) DesktopFormState.saved(featureRequestDialog);
                        ViewAuthUtils.stopImpersonating();
                        UI.getCurrent().navigate(DesktopView.class);
                        UI.getCurrent().getPage().reload();
                    }));
        }
        Div separator = new Div();
        separator.addClassName("retro-menu-separator");
        menuItems.add(separator);
        if (notice.active()) {
            action("home.serviceNotice", VaadinIcon.INFO_CIRCLE, () -> {
                Dialog dialog = new Dialog();
                dialog.setHeaderTitle(t("home.serviceNotice"));
                dialog.setWidth("680px");
                dialog.add(serviceNotice(notice.text()));
                dialog.getFooter().add(new Button(t("desktop.close"), e -> dialog.close()));
                dialog.open();
            });
        }
        externalLink("desktop.googlePlay", "https://play.google.com/store/apps/details?id=com.nitramite.energycontroller",
                "/get_it_on_google_play_badge.svg", "retro-store-icon");
        externalLink("home.buyMeACoffee", "https://buymeacoffee.com/norkator",
                "icons/desktop/coffee.svg", "retro-pixel-icon");
        action("lang.english", VaadinIcon.GLOBE, () -> changeLocale("en"));
        action("lang.finnish", VaadinIcon.GLOBE, () -> changeLocale("fi"));
        action("home.logout", VaadinIcon.SIGN_OUT, this::logout);
        shortcut("home.logout", VaadinIcon.SIGN_OUT, this::logout);
        startMenu.setVisible(false);
        add(startMenu, taskbar);
    }

    private Div serviceNotice(String text) {
        Span heading = new Span(t("home.serviceNotice"));
        Span message = new Span(text);
        message.addClassName("retro-notice-message");
        Div panel = new Div(heading, message);
        panel.addClassName("retro-desktop-notice");
        panel.getElement().setAttribute("role", "note");
        panel.getElement().setAttribute("aria-label", t("home.serviceNotice"));
        panel.getElement().setAttribute("tabindex", "0");
        return panel;
    }

    private void externalLink(String key, String url, String imagePath, String imageClass) {
        for (boolean desktop : new boolean[]{true, false}) {
            Anchor link = new Anchor(url, "");
            link.setTarget("_blank");
            link.getElement().setAttribute("rel", "noopener noreferrer");
            Image icon = new Image(imagePath, "");
            icon.addClassName(imageClass);
            link.add(icon, new Span(t(key)));
            link.addClassName(desktop ? "retro-shortcut" : "retro-menu-item");
            (desktop ? icons : menuItems).add(link);
        }
    }

    private void shortcut(String key, VaadinIcon icon, Runnable runnable) {
        NativeButton button = new NativeButton();
        button.add(pixelIcon(icon), new Span(t(key)));
        button.addClickListener(e -> runnable.run());
        button.addClassName("retro-shortcut");
        icons.add(button);
    }

    private void feature(String key, VaadinIcon icon, Class<? extends Component> destination) {
        featureIcons.put(windowKey(destination.getAnnotation(Route.class).value()), icon);
        shortcut(key, icon, () -> open(destination));
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

    private void logout() {
        confirmDiscard(hasUnsavedChanges(), this::performLogout);
    }

    private void performLogout() {
        windows.values().forEach(w -> DesktopFormState.saved(w.body));
        if (featureRequestDialog != null) DesktopFormState.saved(featureRequestDialog);
        ViewAuthUtils.stopImpersonating();
        VaadinSession.getCurrent().setAttribute("token", null);
        VaadinSession.getCurrent().setAttribute("expiresAt", null);
        UI.getCurrent().navigate(HomeView.class);
    }

    private void changeLocale(String language) {
        confirmDiscard(hasUnsavedChanges(), () -> performLocaleChange(language));
    }

    private void performLocaleChange(String language) {
        windows.values().forEach(w -> DesktopFormState.saved(w.body));
        if (featureRequestDialog != null) DesktopFormState.saved(featureRequestDialog);
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
        DesktopWindow existing = windows.get(windowKey(destination.getAnnotation(Route.class).value()));
        if (existing != null) activate(existing);
        else UI.getCurrent().navigate(destination);
    }

    private Component pixelIcon(VaadinIcon icon) {
        String name = switch (icon) {
            case DESKTOP -> "computer";
            case SLIDERS, COG -> "settings";
            case CLOUD -> "weather";
            case FIRE, FLASH, SUN_O, LIGHTBULB -> "energy";
            case COMMENT -> "feedback";
            case MENU -> "start";
            case SIGN_OUT -> "logout";
            default -> "folder";
        };
        Image image = new Image("icons/desktop/" + name + ".svg", "");
        image.addClassName("retro-pixel-icon");
        return image;
    }

    private void activate(DesktopWindow target) {
        if (activeWindow != target) {
            UI.getCurrent().getPage().getHistory().replaceState(null, target.location);
        }
        activeWindow = target;
        target.frame.setVisible(true);
        windows.values().forEach(w -> {
            w.frame.getElement().getClassList().set("retro-window-active", w == target);
            w.task.getElement().getClassList().set("retro-task-active", w == target);
        });
        target.frame.getElement().executeJs("this.dispatchEvent(new CustomEvent('desktop-raise'))");
    }

    private void close(DesktopWindow target) {
        confirmDiscard(DesktopFormState.isDirty(target.body), () -> closeConfirmed(target));
    }

    private void closeConfirmed(DesktopWindow target) {
        windows.values().remove(target);
        remove(target.frame);
        tasks.remove(target.task);
        if (activeWindow == target) {
            activeWindow = null;
            UI.getCurrent().navigate(DesktopView.class);
        }
    }

    private final class DesktopWindow {
        final Div frame = new Div();
        final Button task;
        final Div body = new Div();
        final Button back = new Button(t("desktop.back"), VaadinIcon.ARROW_LEFT.create());
        final Div toolbar = new Div(back);
        final SubMenu fileMenu;
        String location;

        DesktopWindow(String path, HasElement content) {
            location = path;
            String title = titleFor(path);
            var account = ViewAuthUtils.findAuthenticatedAccount(authService);
            frame.getElement().setAttribute("data-window-key", (account != null ? account.getId() : "guest") + ":" + windowKey(path));
            frame.addClassNames("retro-window", "retro-window-maximized");
            frame.getElement().setAttribute("role", "region");
            frame.getElement().setAttribute("aria-label", title);
            frame.getStyle().set("--window-offset", (windows.size() % 6 * 22) + "px");
            Div titleBar = new Div();
            titleBar.addClassName("retro-titlebar");
            titleBar.add(pixelIcon(iconFor(path)), new Span(title));
            Div controls = new Div();
            controls.addClassName("retro-window-controls");
            controls.add(chrome("_", "desktop.minimize", this::minimize),
                    chrome("□", "desktop.maximize", this::maximize),
                    chrome("×", "desktop.close", () -> close(this)));
            titleBar.add(controls);
            MenuBar menus = new MenuBar();
            menus.addClassName("retro-window-menubar");
            fileMenu = menus.addItem(t("desktop.file")).getSubMenu();
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
            body.addClassName("retro-window-body");
            setContent(content);
            NativeButton resize = new NativeButton();
            resize.addClassName("retro-resize-handle");
            resize.getElement().setAttribute("aria-label", t("desktop.resize"));
            resize.getElement().setAttribute("title", t("desktop.resize"));
            toolbar.addClassName("retro-window-toolbar");
            back.addClickListener(e -> UI.getCurrent().navigate(parentRoute(location)));
            updateLocation(path);
            frame.add(titleBar, menus, toolbar, body, resize);
            task = new Button(title, pixelIcon(iconFor(path)), e -> {
                if (activeWindow == this && frame.isVisible()) minimize();
                else activate(this);
            });
            task.addClassName("retro-task-button");
            frame.getElement().addEventListener("pointerdown", e -> {
                if (activeWindow != this) activate(this);
            });
            frame.getElement().addEventListener("desktop-maximize", e -> maximize());
            frame.getElement().addEventListener("desktop-restore-geometry", e ->
                    frame.getElement().getClassList().set("retro-window-maximized", e.getEventData().get("event.detail.maximized").asBoolean()))
                    .addEventData("event.detail.maximized");
            frame.addAttachListener(e -> frame.getElement().executeJs("window.initDesktopWindow(this)"));
        }

        void updateLocation(String location) {
            this.location = location;
            boolean canGoBack = !parentRoute(location).equals("desktop");
            back.setEnabled(canGoBack);
            toolbar.setVisible(canGoBack);
        }

        void setContent(HasElement content) {
            body.getElement().removeAllChildren();
            body.getElement().appendChild(content.getElement());
            fileMenu.removeAll();
            if (content instanceof Component component) {
                Button creation = DesktopCreateDialog.creationButton(component);
                if (creation != null) {
                    fileMenu.addItem(creation.getText(), event -> creation.click());
                }
            }
            fileMenu.addItem(t("desktop.close"), event -> close(this));
        }

        void minimize() {
            frame.setVisible(false);
            task.removeClassName("retro-task-active");
            if (activeWindow == this) activeWindow = null;
        }

        void maximize() {
            frame.getElement().getClassList().set("retro-window-maximized",
                    !frame.getClassNames().contains("retro-window-maximized"));
            frame.getElement().executeJs("this.dispatchEvent(new CustomEvent('desktop-save-geometry'))");
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
        deviceStatus.refresh();
        var effectiveAccount = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (effectiveAccount == null || !effectiveAccount.isAdmin()) {
            windows.entrySet().removeIf(entry -> {
                if (!entry.getKey().startsWith("admin/") && !entry.getKey().equals("admin")) return false;
                DesktopWindow window = entry.getValue();
                remove(window.frame);
                tasks.remove(window.task);
                if (activeWindow == window) activeWindow = null;
                return true;
            });
        }
        String path = event.getLocation().getPath();
        if (!path.equals("desktop") && pendingContent != null) {
            String key = windowKey(path);
            DesktopWindow next = windows.get(key);
            if (next == null) {
                next = new DesktopWindow(path, pendingContent);
                windows.put(key, next);
                add(next.frame);
                tasks.add(next.task);
            } else {
                next.setContent(pendingContent);
            }
            next.updateLocation(event.getLocation().getPathWithQueryParameters());
            // Routing already changed the URL; only taskbar/Start activation needs to sync it.
            activeWindow = next;
            activate(next);
        }
        pendingContent = null;
        setStartOpen(false);
    }

    static String parentRoute(String location) {
        String path = location.split("\\?", 2)[0];
        return path.contains("/") ? windowKey(path) : "desktop";
    }

    private boolean hasUnsavedChanges() {
        return (featureRequestDialog != null && DesktopFormState.isDirty(featureRequestDialog))
                || windows.values().stream().anyMatch(w -> DesktopFormState.isDirty(w.body));
    }

    private void confirmDiscard(boolean dirty, Runnable proceed) {
        confirmDiscard(dirty, proceed, () -> {});
    }

    private void confirmDiscard(boolean dirty, Runnable proceed, Runnable cancel) {
        if (!dirty) {
            proceed.run();
            return;
        }
        Dialog dialog = new Dialog();
        add(dialog);
        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) remove(dialog);
        });
        dialog.setHeaderTitle(t("desktop.unsavedTitle"));
        dialog.setCloseOnOutsideClick(false);
        dialog.setCloseOnEsc(false);
        dialog.add(new Span(t("desktop.unsavedMessage")));
        dialog.getFooter().add(new Button(t("common.cancel"), e -> { dialog.close(); cancel.run(); }),
                new Button(t("desktop.discard"), e -> {
                    dialog.close();
                    proceed.run();
                }));
        dialog.open();
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        String destination = event.getLocation().getPath();
        boolean leavingDesktop = event.getNavigationTarget().getAnnotation(Route.class) == null
                || event.getNavigationTarget().getAnnotation(Route.class).layout() != MainLayout.class;
        DesktopWindow replaced = windows.get(windowKey(destination));
        boolean dirty = leavingDesktop ? hasUnsavedChanges()
                : replaced != null && DesktopFormState.isDirty(replaced.body);
        if (dirty) {
            var continuation = event.postpone();
            confirmDiscard(true, () -> {
                if (leavingDesktop) {
                    windows.values().forEach(w -> DesktopFormState.saved(w.body));
                    if (featureRequestDialog != null) DesktopFormState.saved(featureRequestDialog);
                } else DesktopFormState.saved(replaced.body);
                continuation.proceed();
            }, () -> {
                continuation.cancel();
                if (activeWindow != null) UI.getCurrent().getPage().getHistory().replaceState(null, activeWindow.location);
            });
        }
    }

    // List and detail routes share a feature window, including direct links and browser Back.
    static String windowKey(String path) {
        String root = path.split("/", 2)[0];
        return switch (root) {
            case "production-source" -> "production-sources";
            case "power-limit" -> "power-limits";
            default -> root;
        };
    }

    private VaadinIcon iconFor(String path) {
        return featureIcons.getOrDefault(windowKey(path), VaadinIcon.FOLDER);
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
