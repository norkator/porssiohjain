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

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.tuya.TuyaService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class TuyaDevicePicker extends VerticalLayout {
    private final TuyaService service;
    private final I18nService i18n;
    private final Supplier<Long> account;
    private final TuyaTask task = new TuyaTask(this);
    private final ComboBox<TuyaService.ConnectionStatus> connections;
    private final ComboBox<TuyaService.Home> homes;
    private final ComboBox<TuyaService.CloudDevice> devices;
    private final Button load;
    private final Button connect;
    private final Span status = new Span();

    TuyaDevicePicker(TuyaService service, I18nService i18n, Supplier<Long> account,
                     Runnable openConnection, Consumer<TuyaService.CloudDevice> selected) {
        this.service = service;
        this.i18n = i18n;
        this.account = account;
        setPadding(false);
        connections = new ComboBox<>(i18n.t("tuya.account"));
        connections.setWidthFull();
        connections.setItemLabelGenerator(TuyaService.ConnectionStatus::label);
        homes = new ComboBox<>(i18n.t("tuya.home"));
        homes.setItemLabelGenerator(home -> home.name() + " (" + home.id() + ")");
        devices = new ComboBox<>(i18n.t("tuya.device"));
        devices.setItemLabelGenerator(device -> device.name() + " (" + device.category() + ", " + device.id() + ")");
        devices.setHelperText(i18n.t("tuya.device.help"));
        homes.setWidthFull();
        devices.setWidthFull();
        load = new Button(i18n.t("tuya.loadDevices"), event -> loadConnections());
        connect = new Button(i18n.t("tuya.connection.title"), event -> openConnection.run());
        HorizontalLayout buttons = new HorizontalLayout(connect, load);
        buttons.setWrap(true);
        add(buttons, connections, homes, devices, status);
        connections.addValueChangeListener(event -> {
            clearDiscovery();
            if (event.getValue() != null) loadHomes(event.getValue().id());
        });
        homes.addValueChangeListener(event -> {
            devices.clear();
            devices.setItems(java.util.List.of());
            if (event.getValue() != null) loadDevices(event.getValue());
        });
        devices.addValueChangeListener(event -> selected.accept(event.getValue()));
        DesktopFormState.watch(this);
    }

    void reset() {
        connections.clear();
        connections.setItems(java.util.List.of());
        clearDiscovery();
    }

    Long connectionId() {
        return connections.getValue() == null ? null : connections.getValue().id();
    }

    private void clearDiscovery() {
        task.cancel();
        homes.clear();
        homes.setItems(java.util.List.of());
        devices.clear();
        devices.setItems(java.util.List.of());
        status.setText("");
        busy(false);
    }

    private void loadConnections() {
        Long accountId = account.get();
        if (accountId == null) return;
        reset();
        var result = service.listConnections(accountId).stream().filter(TuyaService.ConnectionStatus::connected).toList();
        connections.setItems(result);
        status.setText(i18n.t(result.isEmpty() ? "tuya.connection.none" : "tuya.account.choose"));
        if (result.size() == 1) connections.setValue(result.getFirst());
    }

    private void loadHomes(Long connectionId) {
        Long accountId = account.get();
        if (accountId == null) return;
        busy(true);
        task.run(() -> service.homes(accountId, connectionId), result -> {
            if (!Objects.equals(accountId, account.get())) return;
            busy(false);
            homes.setItems(result);
            status.setText(result.isEmpty() ? i18n.t("tuya.homes.empty") : i18n.t("tuya.chooseHome"));
            if (result.size() == 1) homes.setValue(result.getFirst());
        }, error -> failed(accountId, error));
    }

    private void loadDevices(TuyaService.Home home) {
        Long accountId = account.get();
        if (accountId == null) return;
        busy(true);
        Long connectionId = connectionId();
        task.run(() -> service.devices(accountId, connectionId, home.id()), result -> {
            if (!Objects.equals(accountId, account.get())) return;
            busy(false);
            devices.setItems(result);
            status.setText(result.isEmpty() ? i18n.t("tuya.devices.empty") : i18n.t("tuya.chooseDevice"));
        }, error -> failed(accountId, error));
    }

    private void failed(Long accountId, Throwable error) {
        if (!Objects.equals(accountId, account.get())) return;
        busy(false);
        status.setText(TuyaTask.error(i18n, error));
    }

    private void busy(boolean busy) {
        connections.setEnabled(!busy);
        homes.setEnabled(!busy);
        devices.setEnabled(!busy);
        load.setEnabled(!busy);
        connect.setEnabled(!busy);
        if (busy) status.setText(i18n.t("tuya.loading"));
    }
}
