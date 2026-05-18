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
import com.nitramite.porssiohjain.entity.enums.QrLoginStatus;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.LoginResponse;
import com.nitramite.porssiohjain.services.models.QrLoginChallengeResponse;
import com.nitramite.porssiohjain.services.models.QrLoginStatusResponse;
import com.nitramite.porssiohjain.services.QrLoginService;
import com.nitramite.porssiohjain.services.models.CreateQrLoginChallengeRequest;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
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

        getStyle().set("display", "flex");
        getStyle().set("flex-direction", "column");
        getStyle().set("align-items", "center");
        getStyle().set("justify-content", "center");
        getStyle().set("min-height", "100vh");
        getStyle().set("overflow", "auto");

        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setMaxWidth("400px");
        formLayout.setPadding(true);
        formLayout.setSpacing(true);
        formLayout.setAlignItems(Alignment.STRETCH);
        formLayout.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        formLayout.getStyle().set("border-radius", "12px");
        formLayout.getStyle().set("padding", "24px");
        formLayout.getStyle().set("background-color", "var(--lumo-base-color)");

        H2 title = new H2(t("login.title"));
        title.getStyle().set("margin-bottom", "0");

        TextField uuidField = new TextField(t("login.field.uuid"));
        PasswordField secretField = new PasswordField(t("login.field.secret"));

        Button loginButton = new Button(t("login.button.login"), event -> {
            try {
                UUID uuid = UUID.fromString(uuidField.getValue().trim());
                String secret = secretField.getValue().trim();

                String ip = VaadinRequest.getCurrent().getRemoteAddr();
                LoginResponse response = authService.login(ip, uuid, secret);

                VaadinSession.getCurrent().setAttribute("token", response.getToken());
                VaadinSession.getCurrent().setAttribute("expiresAt", response.getExpiresAt());

                Notification notification = Notification.show(t("login.notification.success"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().navigate(HomeView.class);
            } catch (Exception e) {
                Notification notification = Notification.show(t("login.notification.failed", e.getMessage()));
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.setWidthFull();

        Button qrLoginButton = new Button(t("login.button.qr"), event -> openQrLoginDialog());
        qrLoginButton.setWidthFull();

        Span demoLink = new Span(t("login.demoAccount"));
        demoLink.getStyle()
                .set("color", "var(--lumo-primary-text-color)")
                .set("cursor", "pointer")
                .set("font-size", "0.9em")
                .set("text-decoration", "underline");
        demoLink.addClickListener(e -> {
            uuidField.setValue(DEMO_UUID);
            secretField.setValue(DEMO_SECRET);
            loginButton.focus();
        });

        formLayout.add(title, uuidField, secretField, loginButton, qrLoginButton, demoLink);
        add(formLayout);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (ViewAuthUtils.hasValidSession(authService)) {
            attachEvent.getUI().navigate(HomeView.class);
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
        qrImage.getStyle().set("background", "white");
        qrImage.getStyle().set("border-radius", "12px");
        qrImage.getStyle().set("padding", "12px");

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
            Object result = qrLoginService.completeChallenge(qrChallenge.getChallengeId(), qrChallenge.getBrowserSecret());
            if (result instanceof LoginResponse loginResponse) {
                VaadinSession.getCurrent().setAttribute("token", loginResponse.getToken());
                VaadinSession.getCurrent().setAttribute("expiresAt", loginResponse.getExpiresAt());
                stopQrPolling();
                qrDialog.close();
                UI.getCurrent().navigate(HomeView.class);
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
