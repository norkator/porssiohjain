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
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.nitramite.porssiohjain.entity.enums.AccountActivitySource;
import com.nitramite.porssiohjain.entity.enums.QrLoginStatus;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.LoginResponse;
import com.nitramite.porssiohjain.services.models.QrLoginChallengeResponse;
import com.nitramite.porssiohjain.services.models.QrLoginStatusResponse;
import com.nitramite.porssiohjain.services.QrLoginService;
import com.nitramite.porssiohjain.services.models.CreateQrLoginChallengeRequest;
import com.nitramite.porssiohjain.views.components.PublicAccessWindow;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

@PageTitle("Pörssiohjain - Login")
@Route("login")
@PermitAll
public class LoginView extends VerticalLayout {

    protected final I18nService i18n;
    private final AuthService authService;
    private final QrLoginService qrLoginService;
    private Dialog qrDialog;
    private QrLoginChallengeResponse qrChallenge;
    private Span qrStatusSpan;
    private Registration qrPollRegistration;
    private int previousPollInterval = -1;

    private static final String DEMO_UUID = "78b7823f-d5cc-4376-8910-cd62e7b32400";
    private static final String DEMO_SECRET = "103058b63f9245099d0c30d81e1636bc";

    @Autowired
    public LoginView(
            AuthService authService,
            I18nService i18n,
            QrLoginService qrLoginService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.qrLoginService = qrLoginService;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        } else {
            Locale defaultLocale = Locale.of("fi", "FI");
            UI.getCurrent().setLocale(defaultLocale);
            VaadinSession.getCurrent().setAttribute(Locale.class, defaultLocale);
        }

        addClassNames("public-access-view", "public-access-login");
        setPadding(false);
        setSpacing(false);

        VerticalLayout formLayout = new VerticalLayout();

        H1 title = new H1(t("login.title"));

        TextField uuidField = new TextField(t("login.field.uuid"));
        uuidField.setRequired(true);
        uuidField.setValueChangeMode(ValueChangeMode.EAGER);
        PasswordField secretField = new PasswordField(t("login.field.secret"));
        secretField.setRequired(true);
        secretField.setValueChangeMode(ValueChangeMode.EAGER);

        Button loginButton = new Button(t("login.button.login"), event -> {
            if (uuidField.getValue().isBlank() || secretField.getValue().isBlank()) {
                return;
            }
            try {
                UUID uuid = UUID.fromString(uuidField.getValue().trim());
                String secret = secretField.getValue().trim();

                String ip = VaadinRequest.getCurrent().getRemoteAddr();
                LoginResponse response = authService.login(ip, uuid, secret, AccountActivitySource.VAADIN);

                VaadinSession.getCurrent().setAttribute("token", response.getToken());
                VaadinSession.getCurrent().setAttribute("expiresAt", response.getExpiresAt());

                Notification notification = Notification.show(t("login.notification.success"));
                notification.setPosition(Notification.Position.TOP_END);
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().navigate(DesktopView.class);
            } catch (Exception e) {
                Notification notification = Notification.show(t("login.notification.failed", e.getMessage()));
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.setWidthFull();
        loginButton.setEnabled(false);
        uuidField.addValueChangeListener(event -> loginButton.setEnabled(
                !uuidField.getValue().isBlank() && !secretField.getValue().isBlank()));
        secretField.addValueChangeListener(event -> loginButton.setEnabled(
                !uuidField.getValue().isBlank() && !secretField.getValue().isBlank()));

        Button qrLoginButton = new Button(t("login.button.qr"), event -> openQrLoginDialog());
        qrLoginButton.setWidthFull();

        Button demoLink = new Button(t("login.demoAccount"));
        demoLink.addClassName("public-access-demo");
        demoLink.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        demoLink.addClickListener(e -> {
            uuidField.setValue(DEMO_UUID);
            secretField.setValue(DEMO_SECRET);
            loginButton.focus();
        });

        Button backButton = new Button(t("createAccount.button.back"), event -> UI.getCurrent().navigate(HomeView.class));
        backButton.addClassName("public-access-back");

        formLayout.add(title, uuidField, secretField, loginButton, qrLoginButton, demoLink, backButton);
        add(new PublicAccessWindow(t("login.title"), formLayout));
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (ViewAuthUtils.hasValidSession(authService)) {
            attachEvent.getUI().navigate(DesktopView.class);
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopQrPolling();
        cancelQrChallenge();
        super.onDetach(detachEvent);
    }

    private void openQrLoginDialog() {
        try {
            String ip = VaadinRequest.getCurrent().getRemoteAddr();
            String apiBaseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .build()
                    .toUriString();

            CreateQrLoginChallengeRequest request = new CreateQrLoginChallengeRequest();
            request.setBrowserName(VaadinRequest.getCurrent().getHeader("User-Agent"));
            request.setTimeZone(ZoneId.systemDefault().getId());

            qrChallenge = qrLoginService.createChallenge(ip, apiBaseUrl, request);

            qrDialog = buildQrDialog(qrChallenge);
            qrDialog.open();
            startQrPolling();
        } catch (Exception e) {
            showError(t("login.notification.failed", e.getMessage()));
        }
    }

    private Dialog buildQrDialog(QrLoginChallengeResponse challenge) throws IOException, WriterException {
        Dialog dialog = new Dialog();
        dialog.addClassName("public-access-dialog");
        dialog.setHeaderTitle(t("login.qr.title"));
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("420px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        Span eyebrow = new Span(t("login.qr.eyebrow"));
        eyebrow.getElement().getThemeList().add("badge");

        Image qrImage = new Image(createQrCodeBytes(challenge.getQrPayload()), t("login.qr.imageAlt"), "image/png");
        qrImage.setWidthFull();
        qrImage.addClassName("public-access-qr");

        Paragraph description = new Paragraph(t("login.qr.description"));
        description.getStyle().set("margin", "0");

        qrStatusSpan = new Span(t("login.qr.waiting"));
        qrStatusSpan.getElement().setAttribute("theme", "badge contrast");

        Button cancelButton = new Button(t("button.cancel"), event -> closeQrDialog());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout footer = new HorizontalLayout(cancelButton);
        footer.setWidthFull();
        footer.setJustifyContentMode(JustifyContentMode.END);

        content.add(eyebrow, qrImage, description, qrStatusSpan, footer);
        dialog.add(content);
        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) {
                closeQrDialog();
            }
        });
        return dialog;
    }

    private byte[] createQrCodeBytes(String payload) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(payload, BarcodeFormat.QR_CODE, 320, 320);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(MatrixToImageWriter.toBufferedImage(bitMatrix), "PNG", outputStream);
        return outputStream.toByteArray();
    }

    private void startQrPolling() {
        stopQrPolling();
        UI ui = UI.getCurrent();
        previousPollInterval = ui.getPollInterval();
        ui.setPollInterval(qrChallenge.getPollIntervalMs());
        qrPollRegistration = ui.addPollListener(event -> pollQrLoginStatus());
    }

    private void stopQrPolling() {
        UI ui = UI.getCurrent();
        if (qrPollRegistration != null) {
            qrPollRegistration.remove();
            qrPollRegistration = null;
        }
        if (ui != null) {
            ui.setPollInterval(previousPollInterval);
        }
        previousPollInterval = -1;
    }

    private void pollQrLoginStatus() {
        if (qrChallenge == null || qrDialog == null || !qrDialog.isOpened()) {
            stopQrPolling();
            return;
        }

        if (qrChallenge.getExpiresAt().isBefore(Instant.now())) {
            updateQrStatus(t("login.qr.expired"), true);
            stopQrPolling();
            return;
        }

        try {
            Object result = qrLoginService.completeChallenge(
                    qrChallenge.getChallengeId(),
                    qrChallenge.getBrowserSecret(),
                    AccountActivitySource.VAADIN
            );
            if (result instanceof LoginResponse loginResponse) {
                VaadinSession.getCurrent().setAttribute("token", loginResponse.getToken());
                VaadinSession.getCurrent().setAttribute("expiresAt", loginResponse.getExpiresAt());
                stopQrPolling();
                qrDialog.close();
                UI.getCurrent().navigate(DesktopView.class);
                return;
            }

            if (result instanceof QrLoginStatusResponse statusResponse && statusResponse.getStatus() == QrLoginStatus.PENDING) {
                updateQrStatus(t("login.qr.waiting"), false);
            } else if (result instanceof QrLoginStatusResponse statusResponse) {
                updateQrStatus(mapQrStatusMessage(statusResponse.getStatus()), true);
                stopQrPolling();
            }
        } catch (ResponseStatusException e) {
            updateQrStatus(mapQrErrorMessage(e), true);
            stopQrPolling();
        } catch (Exception e) {
            updateQrStatus(t("login.qr.failed"), true);
            stopQrPolling();
        }
    }

    private void updateQrStatus(String message, boolean error) {
        if (qrStatusSpan == null) {
            return;
        }
        qrStatusSpan.setText(message);
        qrStatusSpan.getElement().setAttribute("theme", error ? "badge error" : "badge contrast");
    }

    private void closeQrDialog() {
        stopQrPolling();
        cancelQrChallenge();
        if (qrDialog != null) {
            Dialog currentDialog = qrDialog;
            qrDialog = null;
            qrStatusSpan = null;
            if (currentDialog.isOpened()) {
                currentDialog.close();
            }
        }
    }

    private void cancelQrChallenge() {
        if (qrChallenge == null) {
            return;
        }
        try {
            qrLoginService.cancelChallenge(qrChallenge.getChallengeId(), qrChallenge.getBrowserSecret());
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        } finally {
            qrChallenge = null;
        }
    }

    private void showError(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private String mapQrStatusMessage(QrLoginStatus status) {
        if (status == QrLoginStatus.EXPIRED) {
            return t("login.qr.expired");
        }
        return t("login.qr.failed");
    }

    private String mapQrErrorMessage(ResponseStatusException exception) {
        String reason = exception.getReason();
        if (reason != null && reason.toLowerCase(Locale.ROOT).contains("expired")) {
            return t("login.qr.expired");
        }
        return t("login.qr.failed");
    }

}
