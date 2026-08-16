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
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.PowerplantComparisonType;
import com.nitramite.porssiohjain.entity.enums.PowerplantElementType;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.PowerplantService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerplantElementResponse;
import com.nitramite.porssiohjain.services.models.PowerplantMeasurementOptionResponse;
import com.nitramite.porssiohjain.services.models.PowerplantRuleResponse;
import com.nitramite.porssiohjain.services.models.PowerplantSettingsResponse;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
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
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final AuthService authService;
    private final DeviceService deviceService;
    private final PowerplantService powerplantService;
    protected final I18nService i18n;

    private final Div board = new Div();
    private final Scroller boardScroller = new Scroller(board);
    private final IntegerField boardWidthField = new IntegerField();
    private final IntegerField boardHeightField = new IntegerField();
    private final Button applyBoardSizeButton = new Button();
    private final Grid<PowerplantRuleResponse> ruleGrid = new Grid<>(PowerplantRuleResponse.class, false);

    private Long accountId;
    private int boardWidth = BOARD_WIDTH;
    private int boardHeight = BOARD_HEIGHT;
    private List<DeviceResponse> standardDevices = List.of();
    private List<PowerplantMeasurementOptionResponse> measurementOptions = List.of();
    private List<PowerplantElementResponse> elements = List.of();
    private List<PowerplantRuleResponse> rules = List.of();

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

        PowerplantSettingsResponse settings = powerplantService.getOrCreateSettings(accountId);
        boardWidth = clamp(settings.getBoardWidth(), MIN_BOARD_WIDTH, MAX_BOARD_WIDTH, BOARD_WIDTH);
        boardHeight = clamp(settings.getBoardHeight(), MIN_BOARD_HEIGHT, MAX_BOARD_HEIGHT, BOARD_HEIGHT);
        boardWidthField.setValue(boardWidth);
        boardHeightField.setValue(boardHeight);
        applyBoardSize();

        standardDevices = deviceService.listDevices(accountId, accountId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.STANDARD)
                .sorted(Comparator.comparing(DeviceResponse::getDeviceName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        measurementOptions = powerplantService.getMeasurementOptions(accountId);

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

        Button addRule = new Button(t("powerplant.button.addRule"), VaadinIcon.CONNECT.create(),
                event -> openRuleDialog(null));
        addRule.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button evaluateRules = new Button(t("powerplant.button.evaluateRules"), VaadinIcon.PLAY.create(), event -> {
            int sent = powerplantService.evaluateEnabledRules();
            Notification.show(t("powerplant.notification.rulesEvaluated", sent))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            reloadData();
        });
        evaluateRules.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        VerticalLayout canvasPanel = new VerticalLayout();
        canvasPanel.setPadding(false);
        canvasPanel.setSpacing(true);
        canvasPanel.setWidthFull();
        canvasPanel.addClassName("powerplant-canvas-panel");

        Paragraph hint = new Paragraph(t("powerplant.canvas.hint"));
        hint.getStyle().set("margin", "0");

        HorizontalLayout toolbar = new HorizontalLayout(addElement, addRule, evaluateRules, boardWidthField, boardHeightField, applyBoardSizeButton);
        toolbar.setAlignItems(Alignment.END);
        toolbar.setWrap(true);

        canvasPanel.add(toolbar, hint, boardScroller, createRuleList());
        canvasPanel.setFlexGrow(1, boardScroller);

        card.add(title, intro, canvasPanel);
        add(card);
    }

    private void reloadData() {
        elements = powerplantService.getElements(accountId);
        rules = powerplantService.getRules(accountId);
        ruleGrid.setItems(rules);
        renderBoard();
    }

    private void renderBoard() {
        board.removeAll();

        Element svg = new Element("svg");
        svg.setAttribute("viewBox", "0 0 " + boardWidth + " " + boardHeight);
        svg.setAttribute("class", "powerplant-svg");
        board.getElement().appendChild(svg);

        Map<Long, PowerplantElementResponse> elementById = elements.stream()
                .collect(Collectors.toMap(PowerplantElementResponse::getId, Function.identity()));
        Map<String, List<PowerplantRuleResponse>> rulesByEndpoint = rules.stream()
                .collect(Collectors.groupingBy(rule -> rule.getSourceElement().getId() + "->" + rule.getTargetElement().getId()));
        for (PowerplantRuleResponse rule : rules) {
            PowerplantElementResponse source = elementById.get(rule.getSourceElement().getId());
            PowerplantElementResponse target = elementById.get(rule.getTargetElement().getId());
            if (source != null && target != null) {
                List<PowerplantRuleResponse> parallelRules = rulesByEndpoint.get(rule.getSourceElement().getId() + "->" + rule.getTargetElement().getId());
                int ruleIndex = parallelRules != null ? parallelRules.indexOf(rule) : 0;
                int ruleCount = parallelRules != null ? parallelRules.size() : 1;
                appendRulePath(svg, source, target, rule, ruleIndex, ruleCount);
            }
        }

        for (PowerplantElementResponse element : elements) {
            Div elementCard = new Div();
            elementCard.addClassName("powerplant-element");
            elementCard.addClassName("powerplant-element-" + element.getElementType().name().toLowerCase(Locale.ROOT).replace('_', '-'));
            elementCard.getStyle()
                    .set("left", element.getCanvasX() + "px")
                    .set("top", element.getCanvasY() + "px")
                    .set("width", ELEMENT_WIDTH + "px")
                    .set("min-height", "118px");

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

            Button delete = new Button(VaadinIcon.TRASH.create(), event -> openDeleteElementDialog(element));
            delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            actions.add(edit, delete);
            elementCard.add(header, body, actions);
            board.add(elementCard);
            enableDrag(elementCard, element);
        }
    }

    private Component createRuleList() {
        ruleGrid.removeAllColumns();
        ruleGrid.addColumn(rule -> rule.getSourceElement().getName())
                .setHeader(t("powerplant.rule.source"))
                .setAutoWidth(true);
        ruleGrid.addColumn(this::ruleLabel)
                .setHeader(t("powerplant.rule.condition"))
                .setAutoWidth(true)
                .setFlexGrow(1);
        ruleGrid.addColumn(rule -> rule.getTargetElement().getName())
                .setHeader(t("powerplant.rule.target"))
                .setAutoWidth(true);
        ruleGrid.addColumn(rule -> rule.isEnabled() ? t("common.yes") : t("common.no"))
                .setHeader(t("powerplant.rule.enabled"))
                .setAutoWidth(true);
        ruleGrid.addColumn(rule -> rule.getLastSkipReason() != null ? rule.getLastSkipReason() : "")
                .setHeader(t("powerplant.rule.lastStatus"))
                .setAutoWidth(true)
                .setFlexGrow(1);
        ruleGrid.addComponentColumn(rule -> {
            Button edit = new Button(VaadinIcon.EDIT.create(), event -> openRuleDialog(rule));
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            Button delete = new Button(VaadinIcon.TRASH.create(), event -> openDeleteRuleDialog(rule));
            delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            HorizontalLayout actions = new HorizontalLayout(edit, delete);
            actions.setPadding(false);
            actions.setSpacing(true);
            return actions;
        }).setHeader(t("controlTable.grid.actions"));
        ruleGrid.setItems(rules);
        ruleGrid.setAllRowsVisible(true);
        ruleGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        H2 title = new H2(t("powerplant.rule.listTitle"));
        title.getStyle().set("font-size", "1.1rem").set("margin", "0");
        VerticalLayout section = new VerticalLayout(title, ruleGrid);
        section.addClassName("powerplant-rule-section");
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();
        return section;
    }

    private void appendRulePath(
            Element svg,
            PowerplantElementResponse source,
            PowerplantElementResponse target,
            PowerplantRuleResponse rule,
            int ruleIndex,
            int ruleCount
    ) {
        int x1 = source.getCanvasX() + ELEMENT_WIDTH;
        int y1 = source.getCanvasY() + 58;
        int x2 = target.getCanvasX();
        int y2 = target.getCanvasY() + 58;
        int handleX = rule.getControlPointX() != null ? rule.getControlPointX() : defaultRuleControlX(x1, y1, x2, y2, ruleIndex, ruleCount);
        int handleY = rule.getControlPointY() != null ? rule.getControlPointY() : defaultRuleControlY(x1, y1, x2, y2, ruleIndex, ruleCount);
        int controlX = bezierControlX(x1, x2, handleX);
        int controlY = bezierControlY(y1, y2, handleY);

        Element path = new Element("path");
        path.setAttribute("d", "M " + x1 + " " + y1 + " Q " + controlX + " " + controlY + " " + x2 + " " + y2);
        path.setAttribute("class", rule.isEnabled() ? "powerplant-rule-line" : "powerplant-rule-line disabled");
        svg.appendChild(path);

        Element text = new Element("text");
        text.setAttribute("x", String.valueOf(handleX));
        text.setAttribute("y", String.valueOf(handleY - 12));
        text.setAttribute("class", "powerplant-rule-label");
        text.setText(ruleLabel(rule));
        svg.appendChild(text);

        Element handle = new Element("circle");
        handle.setAttribute("cx", String.valueOf(handleX));
        handle.setAttribute("cy", String.valueOf(handleY));
        handle.setAttribute("r", "8");
        handle.setAttribute("class", "powerplant-rule-handle");
        svg.appendChild(handle);
        enableRuleControlPointDrag(handle, path, text, rule, x1, y1, x2, y2);
    }

    private int defaultRuleControlX(int x1, int y1, int x2, int y2, int ruleIndex, int ruleCount) {
        return clamp((x1 + x2) / 2 + perpendicularOffsetX(x1, y1, x2, y2, ruleIndex, ruleCount), 0, boardWidth, (x1 + x2) / 2);
    }

    private int defaultRuleControlY(int x1, int y1, int x2, int y2, int ruleIndex, int ruleCount) {
        return clamp((y1 + y2) / 2 + perpendicularOffsetY(x1, y1, x2, y2, ruleIndex, ruleCount), 0, boardHeight, (y1 + y2) / 2);
    }

    private int bezierControlX(int x1, int x2, int handleX) {
        return (2 * handleX) - ((x1 + x2) / 2);
    }

    private int bezierControlY(int y1, int y2, int handleY) {
        return (2 * handleY) - ((y1 + y2) / 2);
    }

    private int perpendicularOffsetX(int x1, int y1, int x2, int y2, int ruleIndex, int ruleCount) {
        double length = Math.max(1.0, Math.hypot(x2 - x1, y2 - y1));
        double normalX = -(double) (y2 - y1) / length;
        return (int) Math.round(normalX * parallelRuleOffset(ruleIndex, ruleCount));
    }

    private int perpendicularOffsetY(int x1, int y1, int x2, int y2, int ruleIndex, int ruleCount) {
        double length = Math.max(1.0, Math.hypot(x2 - x1, y2 - y1));
        double normalY = (double) (x2 - x1) / length;
        return (int) Math.round(normalY * parallelRuleOffset(ruleIndex, ruleCount));
    }

    private int parallelRuleOffset(int ruleIndex, int ruleCount) {
        if (ruleCount <= 1) {
            return 0;
        }
        return (int) Math.round((ruleIndex - ((ruleCount - 1) / 2.0)) * 48.0);
    }

    private void enableRuleControlPointDrag(
            Element handle,
            Element path,
            Element text,
            PowerplantRuleResponse rule,
            int x1,
            int y1,
            int x2,
            int y2
    ) {
        handle.executeJs("""
                const handle = this;
                const path = $0;
                const label = $1;
                const svg = handle.ownerSVGElement;
                const viewElement = $6;
                if (handle.__powerplantRuleDragBound) {
                  return;
                }
                handle.__powerplantRuleDragBound = true;
                handle.style.touchAction = 'none';
                let dragging = false;
                const clamp = (value, min, max) => Math.min(Math.max(value, min), max);
                const toSvgPoint = event => {
                  const point = svg.createSVGPoint();
                  point.x = event.clientX;
                  point.y = event.clientY;
                  return point.matrixTransform(svg.getScreenCTM().inverse());
                };
                const moveControl = point => {
                  const cx = clamp(Math.round(point.x), 0, $2);
                  const cy = clamp(Math.round(point.y), 0, $3);
                  const controlX = (2 * cx) - (($4 + $7) / 2);
                  const controlY = (2 * cy) - (($5 + $8) / 2);
                  handle.setAttribute('cx', cx);
                  handle.setAttribute('cy', cy);
                  path.setAttribute('d', `M ${$4} ${$5} Q ${controlX} ${controlY} ${$7} ${$8}`);
                  label.setAttribute('x', cx);
                  label.setAttribute('y', cy - 12);
                  return {cx, cy};
                };
                handle.addEventListener('pointerdown', event => {
                  dragging = true;
                  handle.setPointerCapture(event.pointerId);
                  event.preventDefault();
                  event.stopPropagation();
                });
                handle.addEventListener('pointermove', event => {
                  if (!dragging) {
                    return;
                  }
                  moveControl(toSvgPoint(event));
                });
                const finish = event => {
                  if (!dragging) {
                    return;
                  }
                  dragging = false;
                  const point = moveControl(toSvgPoint(event));
                  handle.releasePointerCapture?.(event.pointerId);
                  viewElement.$server.updateRuleControlPoint($9, point.cx, point.cy);
                };
                handle.addEventListener('pointerup', finish);
                handle.addEventListener('pointercancel', finish);
                """, path, text, boardWidth, boardHeight, x1, y1, getElement(), x2, y2, rule.getId());
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
        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);

        Span value = new Span(formatValue(element));
        value.addClassName("powerplant-indicator-value");
        body.add(value);

        if (element.getMeasurementType() != null) {
            Span meta = new Span(formatMeasurementMeta(element));
            meta.addClassName("powerplant-element-meta");
            meta.addClassName(element.isLatestMeasurementFresh()
                    ? "powerplant-measurement-fresh"
                    : "powerplant-measurement-stale");
            body.add(meta);
        }
        return body;
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
                  const maxLeft = Math.max(0, $0 - element.offsetWidth);
                  const maxTop = Math.max(0, $1 - element.offsetHeight);
                  const nextLeft = clamp(originLeft + (event.clientX - startX), 0, maxLeft);
                  const nextTop = clamp(originTop + (event.clientY - startY), 0, maxTop);
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
                """, boardWidth, boardHeight, element.getId(), getElement());
    }

    @ClientCallable
    private void updateElementPosition(Long elementId, Integer x, Integer y) {
        powerplantService.updateElementPosition(accountId, elementId, x != null ? x : 0, y != null ? y : 0);
        reloadData();
    }

    @ClientCallable
    private void updateRuleControlPoint(Long ruleId, Integer x, Integer y) {
        powerplantService.updateRuleControlPoint(accountId, ruleId, x != null ? x : 0, y != null ? y : 0);
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

        ComboBox<PowerplantMeasurementOptionResponse> measurementCombo = new ComboBox<>(t("powerplant.field.measurement"));
        List<PowerplantMeasurementOptionResponse> dialogMeasurementOptions = measurementOptionsFor(selected);
        measurementCombo.setItems(dialogMeasurementOptions);
        measurementCombo.setItemLabelGenerator(this::measurementOptionLabel);
        measurementCombo.setRenderer(new ComponentRenderer<>(this::createMeasurementOption));
        measurementCombo.setWidthFull();
        findSelectedMeasurementOption(selected, dialogMeasurementOptions).ifPresent(measurementCombo::setValue);

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

        measurementCombo.addValueChangeListener(event -> {
            if (event.getValue() != null && (unitField.getValue() == null || unitField.getValue().isBlank())) {
                unitField.setValue(defaultUnit(event.getValue().getMeasurementType()));
            }
        });

        FormLayout form = new FormLayout(nameField, typeCombo, iconCombo, measurementCombo, valueField, unitField, deviceCombo, channelField, xField, yField);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("420px", 2)
        );

        Runnable updateVisibility = () -> {
            boolean deviceControl = typeCombo.getValue() == PowerplantElementType.DEVICE_CONTROL;
            boolean indicator = typeCombo.getValue() == PowerplantElementType.INDICATOR;
            boolean label = typeCombo.getValue() == PowerplantElementType.LABEL;
            valueField.setVisible(indicator);
            measurementCombo.setVisible(indicator);
            unitField.setVisible(indicator || label);
            deviceCombo.setVisible(deviceControl);
            channelField.setVisible(deviceControl);
        };
        typeCombo.addValueChangeListener(event -> updateVisibility.run());
        updateVisibility.run();

        Button save = new Button(t("common.save"), event -> {
            try {
                Double rawValue = valueField.getValue();
                PowerplantMeasurementOptionResponse selectedMeasurement = measurementCombo.getValue();
                PowerplantElementResponse saved = powerplantService.saveElement(
                        accountId,
                        selected != null ? selected.getId() : null,
                        nameField.getValue(),
                        typeCombo.getValue(),
                        iconCombo.getValue() != null ? iconCombo.getValue().name() : VaadinIcon.COG.name(),
                        rawValue != null ? BigDecimal.valueOf(rawValue) : null,
                        unitField.getValue(),
                        typeCombo.getValue() == PowerplantElementType.DEVICE_CONTROL && deviceCombo.getValue() != null
                                ? deviceCombo.getValue().getId()
                                : selectedMeasurement != null ? selectedMeasurement.getDevice().getId() : null,
                        channelField.getValue(),
                        selectedMeasurement != null ? selectedMeasurement.getMeasurementType() : null,
                        selectedMeasurement != null ? selectedMeasurement.getMeasurementKey() : null,
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

    private void openRuleDialog(PowerplantRuleResponse selected) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(selected == null ? t("powerplant.rule.addTitle") : t("powerplant.rule.editTitle"));
        dialog.setWidth("680px");

        List<PowerplantElementResponse> indicatorElements = elements.stream()
                .filter(element -> element.getElementType() == PowerplantElementType.INDICATOR
                        && element.getDevice() != null
                        && element.getMeasurementType() != null)
                .toList();
        List<PowerplantElementResponse> deviceControlElements = elements.stream()
                .filter(element -> element.getElementType() == PowerplantElementType.DEVICE_CONTROL
                        && element.getDevice() != null)
                .toList();

        ComboBox<PowerplantElementResponse> sourceCombo = new ComboBox<>(t("powerplant.rule.source"));
        sourceCombo.setItems(indicatorElements);
        sourceCombo.setItemLabelGenerator(PowerplantElementResponse::getName);
        sourceCombo.setWidthFull();
        if (selected != null) {
            indicatorElements.stream()
                    .filter(element -> element.getId().equals(selected.getSourceElement().getId()))
                    .findFirst()
                    .ifPresent(sourceCombo::setValue);
        }

        ComboBox<PowerplantElementResponse> targetCombo = new ComboBox<>(t("powerplant.rule.target"));
        targetCombo.setItems(deviceControlElements);
        targetCombo.setItemLabelGenerator(PowerplantElementResponse::getName);
        targetCombo.setWidthFull();
        if (selected != null) {
            deviceControlElements.stream()
                    .filter(element -> element.getId().equals(selected.getTargetElement().getId()))
                    .findFirst()
                    .ifPresent(targetCombo::setValue);
        }

        ComboBox<PowerplantComparisonType> comparisonCombo = new ComboBox<>(t("powerplant.rule.comparison"));
        comparisonCombo.setItems(PowerplantComparisonType.values());
        comparisonCombo.setItemLabelGenerator(this::comparisonLabel);
        comparisonCombo.setValue(selected != null ? selected.getComparisonType() : PowerplantComparisonType.LESS_THAN);
        comparisonCombo.setWidthFull();

        NumberField thresholdField = new NumberField(t("powerplant.rule.threshold"));
        thresholdField.setWidthFull();
        thresholdField.setValue(selected != null && selected.getThresholdValue() != null ? selected.getThresholdValue().doubleValue() : null);

        NumberField hysteresisField = new NumberField(t("powerplant.rule.hysteresis"));
        hysteresisField.setWidthFull();
        hysteresisField.setValue(selected != null && selected.getHysteresisValue() != null ? selected.getHysteresisValue().doubleValue() : null);

        ComboBox<ControlAction> actionCombo = new ComboBox<>(t("powerplant.rule.action"));
        actionCombo.setItems(ControlAction.TURN_ON, ControlAction.TURN_OFF);
        actionCombo.setItemLabelGenerator(action -> t("controlAction." + action.name()));
        actionCombo.setValue(selected != null ? selected.getTargetAction() : ControlAction.TURN_ON);
        actionCombo.setWidthFull();

        IntegerField cooldownField = new IntegerField(t("powerplant.rule.cooldown"));
        cooldownField.setMin(0);
        cooldownField.setStepButtonsVisible(true);
        cooldownField.setValue(selected != null && selected.getCooldownSeconds() != null ? selected.getCooldownSeconds() : 300);
        cooldownField.setWidthFull();

        Checkbox enabledCheckbox = new Checkbox(t("powerplant.rule.enabled"));
        enabledCheckbox.setValue(selected == null || selected.isEnabled());

        FormLayout form = new FormLayout(sourceCombo, targetCombo, comparisonCombo, thresholdField,
                hysteresisField, actionCombo, cooldownField, enabledCheckbox);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("420px", 2)
        );

        Button save = new Button(t("common.save"), event -> {
            try {
                PowerplantRuleResponse saved = powerplantService.saveRule(
                        accountId,
                        selected != null ? selected.getId() : null,
                        sourceCombo.getValue() != null ? sourceCombo.getValue().getId() : null,
                        targetCombo.getValue() != null ? targetCombo.getValue().getId() : null,
                        comparisonCombo.getValue(),
                        thresholdField.getValue() != null ? BigDecimal.valueOf(thresholdField.getValue()) : null,
                        hysteresisField.getValue() != null ? BigDecimal.valueOf(hysteresisField.getValue()) : null,
                        actionCombo.getValue(),
                        enabledCheckbox.getValue(),
                        cooldownField.getValue()
                );
                Notification.show(t("powerplant.notification.ruleSaved", saved.getId()))
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

    private Component createMeasurementOption(PowerplantMeasurementOptionResponse option) {
        VerticalLayout row = new VerticalLayout();
        row.setPadding(false);
        row.setSpacing(false);

        Span title = new Span(measurementOptionLabel(option));
        title.getStyle().set("font-weight", "600");
        Span detail = new Span(formatMeasurementValue(option.getValue(), option.getMeasurementType())
                + " / " + formatInstant(option.getMeasuredAt()));
        detail.getStyle()
                .set("font-size", "0.82rem")
                .set("color", "var(--lumo-secondary-text-color)");

        row.add(title, detail);
        return row;
    }

    private List<PowerplantMeasurementOptionResponse> measurementOptionsFor(PowerplantElementResponse selected) {
        measurementOptions = powerplantService.getMeasurementOptions(accountId);
        List<PowerplantMeasurementOptionResponse> options = new ArrayList<>(measurementOptions);
        if (selected != null
                && selected.getDevice() != null
                && selected.getMeasurementType() != null
                && selected.getMeasurementKey() != null
                && findSelectedMeasurementOption(selected, options).isEmpty()) {
            options.add(PowerplantMeasurementOptionResponse.builder()
                    .device(selected.getDevice())
                    .measurementType(selected.getMeasurementType())
                    .measurementKey(selected.getMeasurementKey())
                    .value(selected.getLatestMeasurementValue())
                    .measuredAt(selected.getLatestMeasuredAt())
                    .receivedAt(selected.getLatestReceivedAt())
                    .build());
        }
        return options;
    }

    private java.util.Optional<PowerplantMeasurementOptionResponse> findSelectedMeasurementOption(
            PowerplantElementResponse selected,
            List<PowerplantMeasurementOptionResponse> options
    ) {
        if (selected == null || selected.getDevice() == null || selected.getMeasurementType() == null) {
            return java.util.Optional.empty();
        }
        return options.stream()
                .filter(option -> option.getDevice() != null
                        && Objects.equals(option.getDevice().getId(), selected.getDevice().getId())
                        && option.getMeasurementType() == selected.getMeasurementType()
                        && Objects.equals(option.getMeasurementKey(), selected.getMeasurementKey()))
                .findFirst();
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
            PowerplantSettingsResponse saved = powerplantService.saveSettings(accountId, boardWidth, boardHeight);
            boardWidth = clamp(saved.getBoardWidth(), MIN_BOARD_WIDTH, MAX_BOARD_WIDTH, BOARD_WIDTH);
            boardHeight = clamp(saved.getBoardHeight(), MIN_BOARD_HEIGHT, MAX_BOARD_HEIGHT, BOARD_HEIGHT);
            boardWidthField.setValue(boardWidth);
            boardHeightField.setValue(boardHeight);
            applyBoardSize();
            renderBoard();
            Notification.show(t("powerplant.notification.settingsSaved"))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
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

    private void openDeleteElementDialog(PowerplantElementResponse element) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("delete.confirmTitle"));
        dialog.add(t("delete.confirmDescription"));

        Button cancelButton = new Button(t("common.cancel"), event -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deleteButton = new Button(t("button.delete"), event -> {
            dialog.close();
            deleteElement(element);
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelButton, deleteButton);
        dialog.open();
    }

    private void openDeleteRuleDialog(PowerplantRuleResponse rule) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("delete.confirmTitle"));
        dialog.add(t("delete.confirmDescription"));

        Button cancelButton = new Button(t("common.cancel"), event -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deleteButton = new Button(t("button.delete"), event -> {
            try {
                powerplantService.deleteRule(accountId, rule.getId());
                dialog.close();
                reloadData();
            } catch (Exception ex) {
                showError(t("powerplant.notification.failed", ex.getMessage()));
            }
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelButton, deleteButton);
        dialog.open();
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
        BigDecimal sourceValue = element.getLatestMeasurementValue() != null
                ? element.getLatestMeasurementValue()
                : element.getDisplayValue();
        String value = sourceValue != null
                ? sourceValue.stripTrailingZeros().toPlainString()
                : "--";
        String unit = element.getDisplayUnit();
        if ((unit == null || unit.isBlank()) && element.getMeasurementType() != null) {
            unit = defaultUnit(element.getMeasurementType());
        }
        return unit != null && !unit.isBlank() ? value + " " + unit : value;
    }

    private String formatMeasurementMeta(PowerplantElementResponse element) {
        String status = element.isLatestMeasurementFresh()
                ? t("powerplant.measurement.fresh")
                : t("powerplant.measurement.stale");
        return status + " / " + formatInstant(element.getLatestMeasuredAt());
    }

    private String formatMeasurementValue(BigDecimal value, ZigbeeMeasurementType type) {
        String numeric = value != null ? value.stripTrailingZeros().toPlainString() : "--";
        return numeric + " " + defaultUnit(type);
    }

    private String ruleLabel(PowerplantRuleResponse rule) {
        return comparisonLabel(rule.getComparisonType())
                + " " + rule.getThresholdValue().stripTrailingZeros().toPlainString()
                + " -> " + t("controlAction." + rule.getTargetAction().name());
    }

    private String comparisonLabel(PowerplantComparisonType type) {
        return switch (type) {
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case EQUAL -> "=";
        };
    }

    private String measurementOptionLabel(PowerplantMeasurementOptionResponse option) {
        if (option == null) {
            return "";
        }
        String deviceName = option.getDevice() != null ? option.getDevice().getDeviceName() : t("powerplant.device.notLinked");
        return deviceName + " / " + t("powerplant.measurement.type." + option.getMeasurementType().name());
    }

    private String defaultUnit(ZigbeeMeasurementType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case TEMPERATURE, THERMOSTAT_SETPOINT -> "°C";
            case HUMIDITY, BATTERY_PERCENTAGE -> "%";
        };
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) {
            return t("powerplant.measurement.missing");
        }
        ZoneId zone = UI.getCurrent() != null && UI.getCurrent().getLocale() != null
                ? ZoneId.systemDefault()
                : ZoneId.systemDefault();
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(UI.getCurrent().getLocale())
                .withZone(zone)
                .format(instant);
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
