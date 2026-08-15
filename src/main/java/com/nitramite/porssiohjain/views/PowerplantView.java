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
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.PowerplantElementType;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.PowerplantService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerplantElementResponse;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@PageTitle("Pörssiohjain - Powerplant")
@Route("powerplant")
@PermitAll
public class PowerplantView extends VerticalLayout implements BeforeEnterObserver {

    private static final int BOARD_WIDTH = 1600;
    private static final int BOARD_HEIGHT = 900;
    private static final int MIN_BOARD_WIDTH = 800;
    private static final int MIN_BOARD_HEIGHT = 500;
    private static final int MAX_BOARD_WIDTH = 4000;
    private static final int MAX_BOARD_HEIGHT = 2400;
    private static final int ELEMENT_WIDTH = 210;
    private static final int ELEMENT_HEIGHT = 118;

    private final AuthService authService;
    private final DeviceService deviceService;
    private final PowerplantService powerplantService;
    protected final I18nService i18n;

    private final Div board = new Div();
    private final Scroller boardScroller = new Scroller(board);
    private final IntegerField boardWidthField = new IntegerField();
    private final IntegerField boardHeightField = new IntegerField();
    private final Button applyBoardSizeButton = new Button();

    private Long accountId;
    private int boardWidth = BOARD_WIDTH;
    private int boardHeight = BOARD_HEIGHT;
    private List<DeviceResponse> standardDevices = List.of();
    private List<PowerplantElementResponse> elements = List.of();

    @Autowired
    public PowerplantView(
            AuthService authService,
            DeviceService deviceService,
            PowerplantService powerplantService,
            I18nService i18n
    ) {
        this.authService = authService;
        this.deviceService = deviceService;
        this.powerplantService = powerplantService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        board.addClassName("powerplant-board");
        applyBoardSize();
        boardScroller.setSizeFull();

        configureBoardSizeControls();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, authService)) {
            return;
        }

        AccountEntity account = ViewAuthUtils.getAuthenticatedAccount(authService, t("powerplant.notification.sessionExpired"));
        if (account == null) {
            return;
        }
        accountId = account.getId();

        standardDevices = deviceService.listDevices(accountId, accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.STANDARD)
                .sorted(Comparator.comparing(DeviceResponse::getDeviceName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        renderView();
        reloadData();
    }

    private void renderView() {
        removeAll();

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);
        card.addClassName("responsive-card");

        H2 title = new H2(t("powerplant.title"));
        title.getStyle().set("margin-top", "0");

        Paragraph intro = new Paragraph(t("powerplant.description"));
        intro.setWidthFull();
        intro.getStyle().set("margin", "0");

        Button addElement = new Button(t("powerplant.button.addElement"), VaadinIcon.PLUS.create(),
                event -> openElementDialog(null));
        addElement.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout canvasPanel = new VerticalLayout();
        canvasPanel.setPadding(false);
        canvasPanel.setSpacing(true);
        canvasPanel.setWidthFull();
        canvasPanel.addClassName("powerplant-canvas-panel");

        Paragraph hint = new Paragraph(t("powerplant.canvas.hint"));
        hint.getStyle().set("margin", "0");

        HorizontalLayout toolbar = new HorizontalLayout(addElement, boardWidthField, boardHeightField, applyBoardSizeButton);
        toolbar.setAlignItems(Alignment.END);
        toolbar.setWrap(true);

        canvasPanel.add(toolbar, hint, boardScroller);
        canvasPanel.setFlexGrow(1, boardScroller);

        card.add(title, intro, canvasPanel);
        add(card);
    }

    private void reloadData() {
        elements = powerplantService.getElements(accountId);
        renderBoard();
    }

    private void renderBoard() {
        board.removeAll();

        for (PowerplantElementResponse element : elements) {
            Div elementCard = new Div();
            elementCard.addClassName("powerplant-element");
            elementCard.addClassName("powerplant-element-" + element.getElementType().name().toLowerCase(Locale.ROOT).replace('_', '-'));
            elementCard.getStyle()
                    .set("left", element.getCanvasX() + "px")
                    .set("top", element.getCanvasY() + "px")
                    .set("width", ELEMENT_WIDTH + "px")
                    .set("height", ELEMENT_HEIGHT + "px");

            HorizontalLayout header = new HorizontalLayout(createElementIcon(element.getIconName()), new Span(element.getName()));
            header.addClassName("powerplant-element-header");
            header.setPadding(false);
            header.setSpacing(true);
            header.setAlignItems(Alignment.CENTER);

            Div body = new Div();
            body.addClassName("powerplant-element-body");
            body.add(createElementBody(element));

            HorizontalLayout actions = new HorizontalLayout();
            actions.addClassName("powerplant-element-actions");
            actions.setPadding(false);
            actions.setSpacing(true);

            Button edit = new Button(VaadinIcon.EDIT.create(), event -> openElementDialog(element));
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button delete = new Button(VaadinIcon.TRASH.create(), event -> deleteElement(element));
            delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            actions.add(edit, delete);
            elementCard.add(header, body, actions);
            board.add(elementCard);
            enableDrag(elementCard, element);
        }
    }

    private Component createElementBody(PowerplantElementResponse element) {
        return switch (element.getElementType()) {
            case INDICATOR -> createIndicatorBody(element);
            case DEVICE_CONTROL -> createDeviceControlBody(element);
            case BUTTON -> createVisualButtonBody(element);
            case EQUIPMENT -> createEquipmentBody(element);
            case LABEL -> createLabelBody(element);
        };
    }

    private Component createIndicatorBody(PowerplantElementResponse element) {
        Span value = new Span(formatValue(element));
        value.addClassName("powerplant-indicator-value");
        return value;
    }

    private Component createDeviceControlBody(PowerplantElementResponse element) {
        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);
        body.addClassName("powerplant-device-control");

        Span meta = new Span(element.getDevice() != null
                ? t("powerplant.device.channelLabel", element.getDevice().getDeviceName(), element.getDeviceChannel())
                : t("powerplant.device.notLinked"));
        meta.addClassName("powerplant-element-meta");

        Button on = new Button(t("powerplant.button.on"), event -> sendDeviceControl(element, true));
        on.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
        Button off = new Button(t("powerplant.button.off"), event -> sendDeviceControl(element, false));
        off.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

        HorizontalLayout controls = new HorizontalLayout(on, off);
        controls.setPadding(false);
        controls.setSpacing(true);
        controls.addClassName("powerplant-device-buttons");

        body.add(meta, controls);
        return body;
    }

    private Component createVisualButtonBody(PowerplantElementResponse element) {
        Button button = new Button(t("powerplant.button.visual"));
        button.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
        button.setEnabled(false);
        return button;
    }

    private Component createEquipmentBody(PowerplantElementResponse element) {
        Span label = new Span(t("powerplant.type." + element.getElementType().name()));
        label.addClassName("powerplant-element-meta");
        return label;
    }

    private Component createLabelBody(PowerplantElementResponse element) {
        Span label = new Span(element.getDisplayUnit() != null ? element.getDisplayUnit() : t("powerplant.label.placeholder"));
        label.addClassName("powerplant-label-text");
        return label;
    }

    private Icon createElementIcon(String iconName) {
        try {
            return VaadinIcon.valueOf(iconName).create();
        } catch (Exception ignored) {
            return VaadinIcon.COG.create();
        }
    }

    private void enableDrag(Div elementCard, PowerplantElementResponse element) {
        elementCard.getElement().executeJs("""
                const element = this;
                const viewElement = $3;
                if (element.__powerplantDragBound) {
                  return;
                }
                element.__powerplantDragBound = true;
                element.style.touchAction = 'none';
                let dragging = false;
                let startX = 0;
                let startY = 0;
                let originLeft = 0;
                let originTop = 0;
                const clamp = (value, min, max) => Math.min(Math.max(value, min), max);
                element.addEventListener('pointerdown', event => {
                  if (event.target.closest('vaadin-button')) {
                    return;
                  }
                  dragging = true;
                  startX = event.clientX;
                  startY = event.clientY;
                  originLeft = parseInt(element.style.left || '0', 10);
                  originTop = parseInt(element.style.top || '0', 10);
                  element.setPointerCapture(event.pointerId);
                });
                element.addEventListener('pointermove', event => {
                  if (!dragging) {
                    return;
                  }
                  const nextLeft = clamp(originLeft + (event.clientX - startX), 0, $0);
                  const nextTop = clamp(originTop + (event.clientY - startY), 0, $1);
                  element.style.left = `${nextLeft}px`;
                  element.style.top = `${nextTop}px`;
                });
                const finish = event => {
                  if (!dragging) {
                    return;
                  }
                  dragging = false;
                  const nextLeft = parseInt(element.style.left || '0', 10);
                  const nextTop = parseInt(element.style.top || '0', 10);
                  element.releasePointerCapture?.(event.pointerId);
                  viewElement.$server.updateElementPosition($2, nextLeft, nextTop);
                };
                element.addEventListener('pointerup', finish);
                element.addEventListener('pointercancel', finish);
                """, boardWidth - ELEMENT_WIDTH, boardHeight - ELEMENT_HEIGHT, element.getId(), getElement());
    }

    @ClientCallable
    private void updateElementPosition(Long elementId, Integer x, Integer y) {
        powerplantService.updateElementPosition(accountId, elementId, x != null ? x : 0, y != null ? y : 0);
        reloadData();
    }

    private void openElementDialog(PowerplantElementResponse selected) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(selected == null ? t("powerplant.dialog.addTitle") : t("powerplant.dialog.editTitle"));
        dialog.setWidth("620px");

        TextField nameField = new TextField(t("powerplant.field.name"));
        nameField.setWidthFull();
        nameField.setValue(selected != null ? selected.getName() : "");

        ComboBox<PowerplantElementType> typeCombo = new ComboBox<>(t("powerplant.field.type"));
        typeCombo.setItems(PowerplantElementType.values());
        typeCombo.setItemLabelGenerator(type -> t("powerplant.type." + type.name()));
        typeCombo.setValue(selected != null ? selected.getElementType() : PowerplantElementType.INDICATOR);
        typeCombo.setWidthFull();

        ComboBox<VaadinIcon> iconCombo = new ComboBox<>(t("powerplant.field.icon"));
        iconCombo.setItems(iconChoices());
        iconCombo.setItemLabelGenerator(VaadinIcon::name);
        iconCombo.setRenderer(new ComponentRenderer<>(this::createIconOption));
        iconCombo.setValue(selected != null ? selectedIcon(selected.getIconName()) : VaadinIcon.FIRE);
        iconCombo.setWidthFull();

        NumberField valueField = new NumberField(t("powerplant.field.value"));
        valueField.setWidthFull();
        valueField.setValue(selected != null && selected.getDisplayValue() != null ? selected.getDisplayValue().doubleValue() : null);

        TextField unitField = new TextField(t("powerplant.field.unit"));
        unitField.setWidthFull();
        unitField.setValue(selected != null && selected.getDisplayUnit() != null ? selected.getDisplayUnit() : "");

        ComboBox<DeviceResponse> deviceCombo = new ComboBox<>(t("powerplant.field.device"));
        deviceCombo.setItems(standardDevices);
        deviceCombo.setItemLabelGenerator(this::deviceLabel);
        deviceCombo.setWidthFull();
        if (selected != null && selected.getDevice() != null) {
            standardDevices.stream()
                    .filter(device -> device.getId().equals(selected.getDevice().getId()))
                    .findFirst()
                    .ifPresent(deviceCombo::setValue);
        }

        IntegerField channelField = new IntegerField(t("powerplant.field.channel"));
        channelField.setMin(0);
        channelField.setMax(3);
        channelField.setStepButtonsVisible(true);
        channelField.setWidthFull();
        channelField.setValue(selected != null && selected.getDeviceChannel() != null ? selected.getDeviceChannel() : 0);

        IntegerField xField = new IntegerField(t("powerplant.field.x"));
        xField.setMin(0);
        xField.setStepButtonsVisible(true);
        xField.setWidthFull();
        xField.setValue(selected != null ? selected.getCanvasX() : nextDefaultX());

        IntegerField yField = new IntegerField(t("powerplant.field.y"));
        yField.setMin(0);
        yField.setStepButtonsVisible(true);
        yField.setWidthFull();
        yField.setValue(selected != null ? selected.getCanvasY() : nextDefaultY());

        FormLayout form = new FormLayout(nameField, typeCombo, iconCombo, valueField, unitField, deviceCombo, channelField, xField, yField);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("420px", 2)
        );

        Runnable updateVisibility = () -> {
            boolean deviceControl = typeCombo.getValue() == PowerplantElementType.DEVICE_CONTROL;
            boolean indicator = typeCombo.getValue() == PowerplantElementType.INDICATOR;
            boolean label = typeCombo.getValue() == PowerplantElementType.LABEL;
            valueField.setVisible(indicator);
            unitField.setVisible(indicator || label);
            deviceCombo.setVisible(deviceControl);
            channelField.setVisible(deviceControl);
        };
        typeCombo.addValueChangeListener(event -> updateVisibility.run());
        updateVisibility.run();

        Button save = new Button(t("common.save"), event -> {
            try {
                Double rawValue = valueField.getValue();
                PowerplantElementResponse saved = powerplantService.saveElement(
                        accountId,
                        selected != null ? selected.getId() : null,
                        nameField.getValue(),
                        typeCombo.getValue(),
                        iconCombo.getValue() != null ? iconCombo.getValue().name() : VaadinIcon.COG.name(),
                        rawValue != null ? BigDecimal.valueOf(rawValue) : null,
                        unitField.getValue(),
                        deviceCombo.getValue() != null ? deviceCombo.getValue().getId() : null,
                        channelField.getValue(),
                        xField.getValue() != null ? xField.getValue() : nextDefaultX(),
                        yField.getValue() != null ? yField.getValue() : nextDefaultY()
                );
                Notification.show(t("powerplant.notification.saved", saved.getName()))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                reloadData();
            } catch (Exception ex) {
                showError(t("powerplant.notification.failed", ex.getMessage()));
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button(t("common.cancel"), event -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private Component createIconOption(VaadinIcon icon) {
        Icon preview = icon.create();
        preview.getStyle()
                .set("width", "1.2rem")
                .set("height", "1.2rem");

        Span name = new Span(icon.name());

        HorizontalLayout option = new HorizontalLayout(preview, name);
        option.setPadding(false);
        option.setSpacing(true);
        option.setAlignItems(Alignment.CENTER);
        return option;
    }

    private void configureBoardSizeControls() {
        boardWidthField.setLabel(t("powerplant.board.width"));
        boardWidthField.setMin(MIN_BOARD_WIDTH);
        boardWidthField.setMax(MAX_BOARD_WIDTH);
        boardWidthField.setStepButtonsVisible(true);
        boardWidthField.setStep(100);
        boardWidthField.setValue(boardWidth);
        boardWidthField.setWidth("150px");

        boardHeightField.setLabel(t("powerplant.board.height"));
        boardHeightField.setMin(MIN_BOARD_HEIGHT);
        boardHeightField.setMax(MAX_BOARD_HEIGHT);
        boardHeightField.setStepButtonsVisible(true);
        boardHeightField.setStep(100);
        boardHeightField.setValue(boardHeight);
        boardHeightField.setWidth("150px");

        applyBoardSizeButton.setText(t("powerplant.button.applyBoardSize"));
        applyBoardSizeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        applyBoardSizeButton.addClickListener(event -> {
            boardWidth = clamp(boardWidthField.getValue(), MIN_BOARD_WIDTH, MAX_BOARD_WIDTH, BOARD_WIDTH);
            boardHeight = clamp(boardHeightField.getValue(), MIN_BOARD_HEIGHT, MAX_BOARD_HEIGHT, BOARD_HEIGHT);
            boardWidthField.setValue(boardWidth);
            boardHeightField.setValue(boardHeight);
            applyBoardSize();
            renderBoard();
        });
    }

    private void applyBoardSize() {
        board.setWidth(boardWidth + "px");
        board.setHeight(boardHeight + "px");
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.min(Math.max(value, min), max);
    }

    private List<VaadinIcon> iconChoices() {
        return List.of(
                VaadinIcon.FIRE,
                VaadinIcon.FLASH,
                VaadinIcon.POWER_OFF,
                VaadinIcon.LIGHTBULB,
                VaadinIcon.COG,
                VaadinIcon.SLIDERS,
                VaadinIcon.WARNING,
                VaadinIcon.DASHBOARD,
                VaadinIcon.DESKTOP,
                VaadinIcon.CLOUD
        );
    }

    private VaadinIcon selectedIcon(String iconName) {
        try {
            return VaadinIcon.valueOf(iconName);
        } catch (Exception ignored) {
            return VaadinIcon.COG;
        }
    }

    private void deleteElement(PowerplantElementResponse element) {
        try {
            powerplantService.deleteElement(accountId, element.getId());
            Notification.show(t("powerplant.notification.deleted", element.getName()))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            reloadData();
        } catch (Exception ex) {
            showError(t("powerplant.notification.failed", ex.getMessage()));
        }
    }

    private void sendDeviceControl(PowerplantElementResponse element, boolean on) {
        try {
            powerplantService.sendDeviceControl(accountId, element.getId(), on);
            Notification.show(t("powerplant.notification.commandSent", element.getName(), on ? t("powerplant.button.on") : t("powerplant.button.off")))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            showError(t("powerplant.notification.failed", ex.getMessage()));
        }
    }

    private String formatValue(PowerplantElementResponse element) {
        String value = element.getDisplayValue() != null
                ? element.getDisplayValue().stripTrailingZeros().toPlainString()
                : "--";
        return element.getDisplayUnit() != null ? value + " " + element.getDisplayUnit() : value;
    }

    private int nextDefaultX() {
        return 40 + (elements.size() % 5) * 240;
    }

    private int nextDefaultY() {
        return 40 + (elements.size() / 5) * 150;
    }

    private String deviceLabel(DeviceResponse device) {
        if (device == null) {
            return "";
        }
        return device.getDeviceName() + " (" + device.getUuid() + ")";
    }

    private void showError(String message) {
        Notification.show(message).addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
