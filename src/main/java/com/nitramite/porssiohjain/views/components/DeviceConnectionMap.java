/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.services.AdminClientCallLogService.DeviceCallLog;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag("device-connection-map")
@JsModule("./device-connection-map.js")
public class DeviceConnectionMap extends Component {
    private final Map<String, String> labels = new LinkedHashMap<>();

    public DeviceConnectionMap(I18nService i18n) {
        for (String key : List.of("search", "zoomIn", "zoomOut", "fit", "viewport", "empty", "noMatches",
                "select", "service", "recent", "older", "controlDevice", "zigbeeGateway", "clientId",
                "clientIp", "lastCall", "endpoint", "clientCount")) {
            labels.put(key, i18n.t("admin.connectionMap." + key));
        }
    }

    public void update(List<DeviceCallLog> calls) {
        List<List<String>> rows = calls.stream().map(call -> List.of(
                call.clientType().name(), text(call.clientId()), text(call.clientIp()),
                call.calledAt().toString(), text(call.endpoint()))).toList();
        getElement().executeJs("this.update($0, $1)", rows, labels);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
