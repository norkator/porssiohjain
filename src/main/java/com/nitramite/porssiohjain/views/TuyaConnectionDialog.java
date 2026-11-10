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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.tuya.TuyaService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Objects;

final class TuyaConnectionDialog extends Dialog {
    private final TuyaService service;
    private final AuthService auth;
    private final I18nService i18n;
    private final Runnable changed;
    private final TuyaTask task = new TuyaTask(this);
    private final TextField userCode;
    private final TextField accountName;
    private final ComboBox<TuyaService.ConnectionStatus> linked = new ComboBox<>();
    private final Button addAccount;
    private final Button rename;
    private final Span status = new Span();
    private final Image qr = new Image();
    private final Button generate;
    private final Button finish;
    private final Button disconnect;
    private TuyaService.QrLogin pending;
    private Long accountId;
    private boolean operationInProgress;

    TuyaConnectionDialog(TuyaService service, AuthService auth, I18nService i18n, Runnable changed) {
        this.service = service;
        this.auth = auth;
        this.i18n = i18n;
        this.changed = changed;
        setHeaderTitle(i18n.t("tuya.connection.title"));
        setWidth("min(620px, 94vw)");
        setMaxHeight("90vh");
        setCloseOnOutsideClick(false);
        linked.setLabel(i18n.t("tuya.account"));
        linked.setWidthFull();
        linked.setItemLabelGenerator(connection -> connection.label() + " — "
                + i18n.t(connection.connected() ? "tuya.account.linked" : "tuya.account.disconnected"));
        linked.addValueChangeListener(event -> selectConnection());
        accountName = new TextField(i18n.t("tuya.accountName"));
        accountName.setRequired(true);
        accountName.setMaxLength(255);
        accountName.setWidthFull();
        accountName.setHelperText(i18n.t("tuya.accountName.help"));
        addAccount = new Button(i18n.t("tuya.account.add"), event -> linked.clear());
        rename = new Button(i18n.t("tuya.account.rename"), event -> rename());
        accountName.addValueChangeListener(event -> {
            clearPending();
            updateGenerateButton();
        });
        userCode = new TextField(i18n.t("tuya.userCode"));
        userCode.setRequired(true);
        userCode.setMaxLength(128);
        userCode.setWidthFull();
        userCode.setHelperText(i18n.t("tuya.userCode.help"));
        userCode.setErrorMessage(i18n.t("tuya.error.userCode"));
        qr.setAlt(i18n.t("tuya.qr.alt"));
        qr.setWidth("280px");
        qr.setHeight("280px");
        qr.getStyle().set("max-width", "100%").set("object-fit", "contain").set("background", "white");
        qr.setVisible(false);
        generate = new Button(i18n.t("tuya.qr.generate"), event -> generate());
        generate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        finish = new Button(i18n.t("tuya.qr.finish"), event -> finish());
        finish.setVisible(false);
        disconnect = new Button(i18n.t("tuya.disconnect"), event -> disconnect());
        disconnect.addThemeVariants(ButtonVariant.LUMO_ERROR);
        userCode.addValueChangeListener(event -> {
            clearPending();
            if (hasUserCode()) userCode.setInvalid(false);
            updateGenerateButton();
        });
        updateGenerateButton();
        VerticalLayout content = new VerticalLayout(new Paragraph(i18n.t("tuya.connection.help")), status,
                linked, addAccount, accountName, rename, userCode, generate, new Paragraph(i18n.t("tuya.qr.instructions")), qr, finish,
                new Paragraph(i18n.t("tuya.disconnect.help")), disconnect);
        content.setPadding(false);
        content.setAlignItems(VerticalLayout.Alignment.STRETCH);
        add(content);
        getFooter().add(new Button(i18n.t("common.close"), event -> close()));
        DesktopFormState.watch(userCode);
        DesktopFormState.watch(accountName);
        addOpenedChangeListener(event -> {
            if (!event.isOpened()) clearPending();
        });
    }

    @Override
    public void open() {
        AccountEntity account = ViewAuthUtils.getAuthenticatedAccount(auth, i18n.t("device.notification.notLoggedIn"));
        if (account == null) return;
        if (!Objects.equals(accountId, account.getId())) {
            clearPending();
            userCode.clear();
        }
        accountId = account.getId();
        reloadConnections(null);
        busy(false);
        super.open();
    }

    private void generate() {
        if (!hasUserCode()) {
            userCode.setInvalid(true);
            userCode.focus();
            return;
        }
        userCode.setInvalid(false);
        String code = userCode.getValue().trim();
        String label = accountName.getValue().trim();
        Long connectionId = selectedId();
        Long requestedAccount = accountId;
        clearPending();
        busy(true);
        task.run(() -> service.beginLogin(requestedAccount, connectionId, label, code), login -> {
            if (!sameAccount()) return;
            pending = login;
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                MatrixToImageWriter.writeToStream(new QRCodeWriter().encode(login.payload(), BarcodeFormat.QR_CODE, 320, 320), "PNG", bytes);
                qr.setSrc("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray()));
                qr.setVisible(true);
                finish.setVisible(true);
                status.setText(i18n.t("tuya.qr.ready"));
            } catch (Exception e) { status.setText(i18n.t("tuya.error.qr")); }
            busy(false);
        }, this::failed);
    }

    private void finish() {
        if (pending == null) return;
        var attempt = pending.id();
        Long requestedAccount = accountId;
        busy(true);
        task.run(() -> service.finishLogin(requestedAccount, attempt), connection -> {
            if (!sameAccount()) return;
            clearPending();
            userCode.clear();
            DesktopFormState.saved(userCode);
            reloadConnections(connection.id());
            busy(false);
            changed.run();
        }, this::failed);
    }

    private void disconnect() {
        Long requestedAccount = accountId;
        Long connectionId = selectedId();
        busy(true);
        task.run(() -> { service.disconnect(requestedAccount, connectionId); return true; }, ignored -> {
            if (!sameAccount()) return;
            clearPending();
            reloadConnections(selectedId());
            busy(false);
            changed.run();
        }, this::failed);
    }

    private Long selectedId() {
        return linked.getValue() == null ? null : linked.getValue().id();
    }

    private void reloadConnections(Long preferredId) {
        var connections = service.listConnections(accountId);
        linked.clear();
        linked.setItems(connections);
        if (!connections.isEmpty()) linked.setValue(connections.stream()
                .filter(connection -> Objects.equals(connection.id(), preferredId)).findFirst().orElse(connections.getLast()));
        selectConnection();
    }

    private void selectConnection() {
        clearPending();
        var connection = linked.getValue();
        accountName.setValue(connection == null ? "" : connection.label());
        userCode.clear();
        DesktopFormState.saved(accountName);
        DesktopFormState.saved(userCode);
        status.setText(connection == null ? i18n.t("tuya.account.new")
                : i18n.t(connection.connected() ? "tuya.connection.saved" : "tuya.account.reconnect", connection.label()));
        disconnect.setVisible(connection != null && connection.connected());
        rename.setVisible(connection != null);
        updateGenerateButton();
    }

    private void rename() {
        if (accountName.getValue().isBlank()) return;
        try {
            Long connectionId = selectedId();
            service.renameConnection(accountId, connectionId, accountName.getValue());
            DesktopFormState.saved(accountName);
            reloadConnections(connectionId);
            changed.run();
        } catch (RuntimeException error) { failed(error); }
    }

    private void failed(Throwable error) {
        if (!sameAccount()) return;
        status.setText(TuyaTask.error(i18n, error));
        busy(false);
    }

    private boolean sameAccount() {
        AccountEntity account = ViewAuthUtils.findAuthenticatedAccount(auth);
        if (account != null && Objects.equals(account.getId(), accountId)) return true;
        close();
        return false;
    }

    private void busy(boolean busy) {
        operationInProgress = busy;
        userCode.setEnabled(!busy);
        accountName.setEnabled(!busy);
        linked.setEnabled(!busy);
        addAccount.setEnabled(!busy);
        rename.setEnabled(!busy);
        updateGenerateButton();
        finish.setEnabled(!busy);
        disconnect.setEnabled(!busy);
        if (busy) status.setText(i18n.t("tuya.loading"));
    }

    private boolean hasUserCode() {
        return !userCode.getValue().isBlank() && userCode.getValue().trim().length() <= 128;
    }

    private void updateGenerateButton() {
        generate.setEnabled(!operationInProgress && hasUserCode() && !accountName.getValue().isBlank());
    }

    private void clearPending() {
        if (pending != null) service.cancelLogin(accountId, pending.id());
        pending = null;
        qr.setVisible(false);
        qr.getElement().removeAttribute("src");
        finish.setVisible(false);
    }
}
