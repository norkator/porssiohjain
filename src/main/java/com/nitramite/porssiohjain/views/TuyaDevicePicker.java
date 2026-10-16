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
        homes = new ComboBox<>(i18n.t("tuya.home"));
        homes.setItemLabelGenerator(home -> home.name() + " (" + home.id() + ")");
        devices = new ComboBox<>(i18n.t("tuya.device"));
        devices.setItemLabelGenerator(device -> device.name() + " (" + device.category() + ", " + device.id() + ")");
        devices.setHelperText(i18n.t("tuya.device.help"));
        homes.setWidthFull();
        devices.setWidthFull();
        load = new Button(i18n.t("tuya.loadDevices"), event -> loadHomes());
        connect = new Button(i18n.t("tuya.connection.title"), event -> openConnection.run());
        HorizontalLayout buttons = new HorizontalLayout(connect, load);
        buttons.setWrap(true);
        add(buttons, homes, devices, status);
        homes.addValueChangeListener(event -> {
            devices.clear();
            devices.setItems(java.util.List.of());
            if (event.getValue() != null) loadDevices(event.getValue());
        });
        devices.addValueChangeListener(event -> selected.accept(event.getValue()));
        DesktopFormState.watch(this);
    }

    void reset() {
        task.cancel();
        homes.clear();
        homes.setItems(java.util.List.of());
        devices.clear();
        devices.setItems(java.util.List.of());
        status.setText("");
        busy(false);
    }

    private void loadHomes() {
        Long accountId = account.get();
        if (accountId == null) return;
        reset();
        busy(true);
        task.run(() -> service.homes(accountId), result -> {
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
        task.run(() -> service.devices(accountId, home.id()), result -> {
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
        homes.setEnabled(!busy);
        devices.setEnabled(!busy);
        load.setEnabled(!busy);
        connect.setEnabled(!busy);
        if (busy) status.setText(i18n.t("tuya.loading"));
    }
}
