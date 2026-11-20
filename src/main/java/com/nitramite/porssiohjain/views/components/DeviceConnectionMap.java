/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.views.components;

import com.nitramite.porssiohjain.services.AdminDeviceConnectionMapService.MapClient;
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
                "clientIp", "lastCall", "endpoint", "clientCount", "heatPump", "thermostat", "temperatureSensor",
                "online", "offline", "unknown", "account", "unassigned", "accountCount", "lastCommunication",
                "connection", "noCall", "deviceName")) {
            labels.put(key, i18n.t("admin.connectionMap." + key));
        }
    }

    public void update(List<MapClient> clients) {
        List<Map<String, Object>> rows = clients.stream().map(client -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", client.type());
            row.put("id", text(client.id()));
            row.put("name", text(client.name()));
            row.put("accountId", client.accountId() == null ? "" : client.accountId().toString());
            row.put("accountEmail", client.accountEmail() == null ? "" : client.accountEmail());
            row.put("online", client.online());
            row.put("connection", text(client.connection()));
            row.put("lastCommunication", client.lastCommunication() == null ? "" : client.lastCommunication().toString());
            row.put("calledAt", client.calledAt() == null ? "" : client.calledAt().toString());
            row.put("ip", text(client.ip()));
            row.put("endpoint", text(client.endpoint()));
            return row;
        }).toList();
        getElement().executeJs("this.update($0, $1)", rows, labels);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
