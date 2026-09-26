package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;

/** Account-scoped desktop summary using the same connectivity flags as My devices. */
final class DesktopDeviceStatus extends NativeButton {
    private final AuthService authService;
    private final DeviceService deviceService;
    private final I18nService i18n;
    private final Span count = new Span();
    private final Span offline = new Span();

    DesktopDeviceStatus(AuthService authService, DeviceService deviceService, I18nService i18n,
                        Runnable openDevices) {
        this.authService = authService;
        this.deviceService = deviceService;
        this.i18n = i18n;
        addClassName("retro-device-status");
        getElement().setAttribute("title", i18n.t("home.myDevices"));
        addClickListener(e -> openDevices.run());
        Span title = new Span(i18n.t("desktop.deviceStatus"));
        title.addClassName("retro-widget-title");
        count.addClassName("retro-device-count");
        count.getElement().setAttribute("aria-live", "polite");
        offline.addClassName("retro-device-offline");
        add(title, count, offline);
        getElement().addEventListener("desktop-status-refresh", e -> refresh());
        addAttachListener(e -> getElement().executeJs("window.initDesktopStatus(this)"));
        refresh();
    }

    void refresh() {
        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null) {
            count.setText("—");
            offline.setText("");
            return;
        }
        var devices = deviceService.getAllDevices(account.getId());
        long total = devices.stream().map(d -> d.getId()).distinct().count();
        long online = devices.stream()
                .filter(d -> Boolean.TRUE.equals(d.getApiOnline()) || Boolean.TRUE.equals(d.getMqttOnline()))
                .map(d -> d.getId()).distinct().count();
        count.setText(i18n.t("desktop.devicesOnline", online, total));
        offline.setText(i18n.t("desktop.devicesOffline", total - online));
    }
}
