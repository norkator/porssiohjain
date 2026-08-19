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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.SolarAngleRecommendationResponse;
import com.nitramite.porssiohjain.services.solar.SolarAnglePlannerService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@PageTitle("Pörssiohjain - Solar Angle Planner")
@Route("solar-angle-planner")
@PermitAll
public class SolarAnglePlannerView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final I18nService i18n;
    private final SolarAnglePlannerService solarAnglePlannerService;
    private final NumberField latitudeField;
    private final NumberField longitudeField;
    private final Select<String> timezoneField;
    private final NumberField currentTiltField;
    private final NumberField currentAzimuthField;
    private final NumberField toleranceField;
    private final Select<String> modeField;
    private final Div azimuthVisualHost;
    private final Div tiltVisualHost;
    private final Span movementSummary;
    private final Span targetSummary;
    private final Span sunSummary;
    private final Span apiSummary;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    @Autowired
    public SolarAnglePlannerView(
            AuthService authService,
            I18nService i18n,
            SolarAnglePlannerService solarAnglePlannerService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.solarAnglePlannerService = solarAnglePlannerService;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        latitudeField = createNumberField(t("solarAngle.field.latitude"), 60.1699);
        longitudeField = createNumberField(t("solarAngle.field.longitude"), 24.9384);
        timezoneField = new Select<>();
        timezoneField.setLabel(t("solarAngle.field.timezone"));
        timezoneField.setItems("Europe/Helsinki", "Europe/Stockholm", "Europe/Tallinn", "UTC");
        timezoneField.setValue("Europe/Helsinki");
        timezoneField.setWidthFull();
        currentTiltField = createNumberField(t("solarAngle.field.currentTilt"), 35.0);
        currentAzimuthField = createNumberField(t("solarAngle.field.currentAzimuth"), 180.0);
        toleranceField = createNumberField(t("solarAngle.field.tolerance"), 2.0);
        modeField = new Select<>();
        modeField.setLabel(t("solarAngle.field.mode"));
        modeField.setItems("MANUAL", "AUTOMATIC_API");
        modeField.setItemLabelGenerator(value -> t("solarAngle.mode." + value));
        modeField.setValue("MANUAL");
        modeField.setWidthFull();

        azimuthVisualHost = new Div();
        azimuthVisualHost.addClassNames("solar-angle-visual", "solar-angle-azimuth-visual");
        tiltVisualHost = new Div();
        tiltVisualHost.addClassNames("solar-angle-visual", "solar-angle-tilt-visual");
        movementSummary = createMetric();
        targetSummary = createMetric();
        sunSummary = createMetric();
        apiSummary = createMetric();

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("padding-top", "20px");

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setMaxWidth("1120px");
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 title = new H2(t("solarAngle.title"));
        title.getStyle().set("margin-top", "0");

        Button calculateButton = new Button(t("solarAngle.button.calculate"), event -> updateRecommendation());
        calculateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button backButton = new Button(t("solarAngle.button.back"), event -> UI.getCurrent().navigate(HomeView.class));
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout actions = new HorizontalLayout(calculateButton, backButton);
        actions.setPadding(false);
        actions.setSpacing(true);

        FlexLayout content = new FlexLayout(createInputPanel(actions), createVisualPanel());
        content.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        content.setWidthFull();
        content.getStyle()
                .set("gap", "var(--lumo-space-l)")
                .set("align-items", "flex-start");

        card.add(title, content);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        AccountEntity account = ViewAuthUtils.getAuthenticatedAccount(authService, t("solarAngle.notification.sessionExpired"));
        if (account == null) {
            return;
        }
        updateRecommendation();
    }

    private VerticalLayout createInputPanel(HorizontalLayout actions) {
        FormLayout form = new FormLayout();
        form.setWidthFull();
        form.add(latitudeField, longitudeField, timezoneField, currentTiltField, currentAzimuthField, toleranceField, modeField);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("520px", 2)
        );

        VerticalLayout panel = new VerticalLayout(new H3(t("solarAngle.inputs.title")), form, actions);
        panel.setPadding(false);
        panel.setSpacing(true);
        panel.setWidth("340px");
        panel.getStyle()
                .set("max-width", "100%")
                .set("flex", "0 0 340px");
        return panel;
    }

    private VerticalLayout createVisualPanel() {
        H3 visualTitle = new H3(t("solarAngle.visual.title"));
        FlexLayout metrics = new FlexLayout(movementSummary, targetSummary, sunSummary, apiSummary);
        metrics.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        metrics.getStyle().set("gap", "var(--lumo-space-s)");
        FlexLayout visuals = new FlexLayout(azimuthVisualHost, tiltVisualHost);
        visuals.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        visuals.setWidthFull();
        visuals.getStyle().set("gap", "var(--lumo-space-m)");

        VerticalLayout panel = new VerticalLayout(visualTitle, visuals, metrics);
        panel.setPadding(false);
        panel.setSpacing(true);
        panel.setFlexGrow(1);
        panel.setMinWidth("320px");
        panel.getStyle().set("flex", "1 1 560px");
        return panel;
    }

    private void updateRecommendation() {
        try {
            ZoneId.of(timezoneField.getValue());
            SolarAngleRecommendationResponse recommendation = solarAnglePlannerService.calculateRecommendation(
                    valueOrDefault(latitudeField, 60.1699),
                    valueOrDefault(longitudeField, 24.9384),
                    timezoneField.getValue(),
                    valueOrDefault(currentTiltField, 35.0),
                    valueOrDefault(currentAzimuthField, 180.0),
                    valueOrDefault(toleranceField, 2.0)
            );
            renderVisual(recommendation);
            updateMetrics(recommendation);
        } catch (DateTimeException | IllegalArgumentException exception) {
            Notification notification = Notification.show(t("solarAngle.notification.invalidInput", exception.getMessage()));
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateMetrics(SolarAngleRecommendationResponse recommendation) {
        String tiltMove = t("solarAngle.direction." + recommendation.getTiltDirection());
        String azimuthMove = t("solarAngle.direction." + recommendation.getAzimuthDirection());
        movementSummary.setText(t("solarAngle.metric.movement", tiltMove, azimuthMove));
        targetSummary.setText(t("solarAngle.metric.target", recommendation.getTargetTilt(), recommendation.getTargetAzimuth()));
        sunSummary.setText(t("solarAngle.metric.sun", recommendation.getSunElevation(), recommendation.getSunAzimuth()));
        apiSummary.setText(t("solarAngle.metric.api", recommendation.getNextCheckAt().format(formatter)));
    }

    private void renderVisual(SolarAngleRecommendationResponse recommendation) {
        renderAzimuthVisual(recommendation);
        renderTiltVisual(recommendation);
    }

    private void renderAzimuthVisual(SolarAngleRecommendationResponse recommendation) {
        double currentAzimuth = normalizeAngle(recommendation.getCurrentAzimuth());
        double targetAzimuth = normalizeAngle(recommendation.getTargetAzimuth());
        double sunAzimuth = normalizeAngle(recommendation.getSunAzimuth());
        double currentX = dialX(currentAzimuth, 88.0);
        double currentY = dialY(currentAzimuth, 88.0);
        double targetX = dialX(targetAzimuth, 98.0);
        double targetY = dialY(targetAzimuth, 98.0);
        double sunX = dialX(sunAzimuth, 70.0);
        double sunY = dialY(sunAzimuth, 70.0);
        String daylightClass = recommendation.isSunVisible() ? "sun-visible" : "sun-hidden";
        String svg = """
                <svg viewBox="0 0 260 260" role="img" aria-label="%s">
                    <rect x="0" y="0" width="260" height="260" class="solar-dial-bg"/>
                    <circle cx="130" cy="130" r="104" class="solar-dial-ring"/>
                    <circle cx="130" cy="130" r="4" class="solar-dial-center"/>
                    <line x1="130" y1="28" x2="130" y2="44" class="solar-dial-major"/>
                    <line x1="130" y1="216" x2="130" y2="232" class="solar-dial-major"/>
                    <line x1="28" y1="130" x2="44" y2="130" class="solar-dial-major"/>
                    <line x1="216" y1="130" x2="232" y2="130" class="solar-dial-major"/>
                    <text x="130" y="22" class="solar-dial-label solar-dial-label-center">0° N</text>
                    <text x="238" y="134" class="solar-dial-label solar-dial-label-end">90° E</text>
                    <text x="130" y="249" class="solar-dial-label solar-dial-label-center">180° S</text>
                    <text x="22" y="134" class="solar-dial-label">270° W</text>
                    <line x1="130" y1="130" x2="%.1f" y2="%.1f" class="solar-azimuth-current"/>
                    <line x1="130" y1="130" x2="%.1f" y2="%.1f" class="solar-azimuth-target"/>
                    <circle cx="%.1f" cy="%.1f" r="8" class="solar-sun %s"/>
                    <circle cx="%.1f" cy="%.1f" r="5" class="solar-current-point"/>
                    <circle cx="%.1f" cy="%.1f" r="5" class="solar-target-point"/>
                    <text x="18" y="24" class="solar-label">%s %.1f°</text>
                    <text x="18" y="44" class="solar-label">%s %.1f°</text>
                </svg>
                """.formatted(
                t("solarAngle.visual.azimuthAria"),
                currentX,
                currentY,
                targetX,
                targetY,
                sunX,
                sunY,
                daylightClass,
                currentX,
                currentY,
                targetX,
                targetY,
                t("solarAngle.visual.current"),
                currentAzimuth,
                t("solarAngle.visual.target"),
                targetAzimuth
        );
        azimuthVisualHost.getElement().setProperty("innerHTML", svg);
    }

    private void renderTiltVisual(SolarAngleRecommendationResponse recommendation) {
        double currentTilt = clamp(recommendation.getCurrentTilt(), 0.0, 90.0);
        double targetTilt = clamp(recommendation.getTargetTilt(), 0.0, 90.0);
        double currentPanelRotation = -currentTilt;
        double targetPanelRotation = -targetTilt;
        double sunX = 210.0 + (Math.cos(Math.toRadians(180.0 - recommendation.getSunElevation())) * 120.0);
        double sunY = 230.0 - (Math.sin(Math.toRadians(recommendation.getSunElevation())) * 135.0);
        String daylightClass = recommendation.isSunVisible() ? "sun-visible" : "sun-hidden";
        String svg = """
                <svg viewBox="0 0 420 280" role="img" aria-label="%s">
                    <rect x="0" y="0" width="420" height="280" class="solar-sky"/>
                    <line x1="36" y1="230" x2="384" y2="230" class="solar-horizon"/>
                    <path d="M 60 230 Q 210 42 360 230" class="solar-arc"/>
                    <circle cx="%.1f" cy="%.1f" r="18" class="solar-sun %s"/>
                    <g transform="translate(210 230)">
                        <line x1="0" y1="0" x2="0" y2="-96" class="solar-target-line" transform="rotate(%.1f)"/>
                        <g transform="rotate(%.1f)">
                            <rect x="-76" y="-10" width="152" height="20" class="solar-target-panel"/>
                        </g>
                        <g transform="rotate(%.1f)">
                            <rect x="-82" y="-14" width="164" height="28" class="solar-current-panel"/>
                            <line x1="-54" y1="-14" x2="-54" y2="14" class="solar-panel-grid"/>
                            <line x1="-27" y1="-14" x2="-27" y2="14" class="solar-panel-grid"/>
                            <line x1="0" y1="-14" x2="0" y2="14" class="solar-panel-grid"/>
                            <line x1="27" y1="-14" x2="27" y2="14" class="solar-panel-grid"/>
                            <line x1="54" y1="-14" x2="54" y2="14" class="solar-panel-grid"/>
                            <line x1="-82" y1="0" x2="82" y2="0" class="solar-panel-grid"/>
                        </g>
                        <line x1="0" y1="0" x2="0" y2="36" class="solar-mast"/>
                        <circle cx="0" cy="0" r="7" class="solar-pivot"/>
                    </g>
                    <text x="28" y="252" class="solar-label">%s %.1f°</text>
                    <text x="250" y="252" class="solar-label">%s %.1f°</text>
                </svg>
                """.formatted(
                t("solarAngle.visual.tiltAria"),
                sunX,
                sunY,
                daylightClass,
                targetPanelRotation,
                targetPanelRotation,
                currentPanelRotation,
                t("solarAngle.visual.current"),
                currentTilt,
                t("solarAngle.visual.target"),
                targetTilt
        );
        tiltVisualHost.getElement().setProperty("innerHTML", svg);
    }

    private NumberField createNumberField(String label, double value) {
        NumberField field = new NumberField(label);
        field.setValue(value);
        field.setWidthFull();
        field.setStep(0.1);
        return field;
    }

    private Span createMetric() {
        Span span = new Span();
        span.addClassNames("solar-angle-metric", LumoUtility.FontSize.SMALL);
        return span;
    }

    private double valueOrDefault(NumberField field, double defaultValue) {
        return field.getValue() != null ? field.getValue() : defaultValue;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double normalizeAngle(double angle) {
        return ((angle % 360.0) + 360.0) % 360.0;
    }

    private double dialX(double azimuth, double radius) {
        return 130.0 + Math.sin(Math.toRadians(azimuth)) * radius;
    }

    private double dialY(double azimuth, double radius) {
        return 130.0 - Math.cos(Math.toRadians(azimuth)) * radius;
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }
}
