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
import com.nitramite.porssiohjain.entity.enums.LoadSheddingTriggerState;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.LoadSheddingService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.LoadSheddingLinkResponse;
import com.nitramite.porssiohjain.services.models.LoadSheddingNodeResponse;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@PageTitle("Pörssiohjain - Load Shedding")
@Route("load-shedding")
@PermitAll
public class LoadSheddingView extends VerticalLayout implements BeforeEnterObserver {

    private static final int BOARD_WIDTH = 1600;
    private static final int BOARD_HEIGHT = 900;
    private static final int NODE_WIDTH = 220;
    private static final int NODE_HEIGHT = 96;

    private final AuthService authService;
    private final DeviceService deviceService;
    private final LoadSheddingService loadSheddingService;
    protected final I18nService i18n;

    private final ComboBox<DeviceResponse> nodeDeviceCombo = new ComboBox<>();
    private final IntegerField nodeChannelField = new IntegerField();
    private final IntegerField nodeXField = new IntegerField();
    private final IntegerField nodeYField = new IntegerField();
    private final Button saveNodeButton = new Button();
    private final Button newNodeButton = new Button();
    private final Button clearNodeButton = new Button();
    private final Button deleteNodeButton = new Button();

    private final ComboBox<LoadSheddingNodeResponse> sourceNodeCombo = new ComboBox<>();
    private final ComboBox<LoadSheddingNodeResponse> targetNodeCombo = new ComboBox<>();
    private final ComboBox<LoadSheddingTriggerState> triggerStateCombo = new ComboBox<>();
    private final ComboBox<ControlAction> targetActionCombo = new ComboBox<>();
    private final Checkbox reverseOnClearCheckbox = new Checkbox();
    private final Button saveLinkButton = new Button();
    private final Button newLinkButton = new Button();
    private final Button clearLinkButton = new Button();
    private final Button deleteLinkButton = new Button();

    private final Grid<LoadSheddingLinkResponse> linkGrid = new Grid<>(LoadSheddingLinkResponse.class, false);
    private final Div board = new Div();
    private final Scroller boardScroller = new Scroller(board);

    private Long accountId;
    private List<DeviceResponse> standardDevices = List.of();
    private List<LoadSheddingNodeResponse> nodes = List.of();
    private List<LoadSheddingLinkResponse> links = List.of();
    private LoadSheddingNodeResponse selectedNode;
    private LoadSheddingLinkResponse selectedLink;

    @Autowired
    public LoadSheddingView(
            AuthService authService,
            DeviceService deviceService,
            LoadSheddingService loadSheddingService,
            I18nService i18n
    ) {
        this.authService = authService;
        this.deviceService = deviceService;
        this.loadSheddingService = loadSheddingService;
        this.i18n = i18n;

        Locale storedLocale = VaadinSession.getCurrent().getAttribute(Locale.class);
        if (storedLocale != null) {
            UI.getCurrent().setLocale(storedLocale);
        }

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        board.addClassName("load-shedding-board");
        board.setWidth(BOARD_WIDTH + "px");
        board.setHeight(BOARD_HEIGHT + "px");
        boardScroller.setSizeFull();

        nodeDeviceCombo.setLabel(t("loadShedding.node.device"));
        nodeChannelField.setLabel(t("loadShedding.node.channel"));
        nodeXField.setLabel(t("loadShedding.node.x"));
        nodeYField.setLabel(t("loadShedding.node.y"));
        saveNodeButton.setText(t("loadShedding.button.saveNode"));
        newNodeButton.setText(t("loadShedding.button.newNode"));
        clearNodeButton.setText(t("loadShedding.button.clear"));
        deleteNodeButton.setText(t("loadShedding.button.deleteNode"));
        sourceNodeCombo.setLabel(t("loadShedding.link.source"));
        targetNodeCombo.setLabel(t("loadShedding.link.target"));
        triggerStateCombo.setLabel(t("loadShedding.link.trigger"));
        targetActionCombo.setLabel(t("loadShedding.link.action"));
        reverseOnClearCheckbox.setLabel(t("loadShedding.link.reverseOnClear"));
        saveLinkButton.setText(t("loadShedding.button.saveLink"));
        newLinkButton.setText(t("loadShedding.button.newLink"));
        clearLinkButton.setText(t("loadShedding.button.clear"));
        deleteLinkButton.setText(t("loadShedding.button.deleteLink"));

        configureNodeForm();
        configureLinkForm();
        configureLinkGrid();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, authService)) {
            return;
        }

        AccountEntity account = ViewAuthUtils.getAuthenticatedAccount(authService, t("loadShedding.notification.sessionExpired"));
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

        H2 title = new H2(t("loadShedding.title"));
        title.getStyle().set("margin-top", "0");

        Paragraph intro = new Paragraph(t("loadShedding.description"));
        intro.setWidthFull();
        intro.getStyle().set("margin", "0");

        HorizontalLayout content = new HorizontalLayout(createEditorPanel(), createCanvasPanel());
        content.setWidthFull();
        content.setSpacing(true);
        content.setAlignItems(Alignment.START);
        content.setFlexGrow(0, content.getComponentAt(0));
        content.setFlexGrow(1, content.getComponentAt(1));

        card.add(title, intro, content);
        add(card);
    }

    private Component createEditorPanel() {
        VerticalLayout editor = new VerticalLayout();
        editor.addClassName("load-shedding-editor");
        editor.setWidth("380px");
        editor.setPadding(false);
        editor.setSpacing(true);

        editor.add(
                createNodeEditorCard(),
                createLinkEditorCard(),
                createLinkGridCard()
        );
        return editor;
    }

    private Component createCanvasPanel() {
        VerticalLayout canvasPanel = new VerticalLayout();
        canvasPanel.setPadding(false);
        canvasPanel.setSpacing(true);
        canvasPanel.setWidthFull();
        canvasPanel.addClassName("load-shedding-canvas-panel");

        Paragraph hint = new Paragraph(t("loadShedding.canvas.hint"));
        hint.getStyle().set("margin", "0");

        canvasPanel.add(hint, boardScroller);
        canvasPanel.setFlexGrow(1, boardScroller);
        return canvasPanel;
    }

    private Component createNodeEditorCard() {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("load-shedding-section");
        card.setPadding(false);
        card.setSpacing(true);

        H2 title = new H2(t("loadShedding.node.title"));
        title.getStyle().set("font-size", "1.1rem");
        title.getStyle().set("margin", "0");

        FormLayout formLayout = new FormLayout(nodeDeviceCombo, nodeChannelField, nodeXField, nodeYField);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("300px", 2)
        );

        HorizontalLayout actions = new HorizontalLayout(saveNodeButton, newNodeButton, clearNodeButton, deleteNodeButton);
        actions.setWrap(true);

        card.add(title, formLayout, actions);
        return card;
    }

    private Component createLinkEditorCard() {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("load-shedding-section");
        card.setPadding(false);
        card.setSpacing(true);

        H2 title = new H2(t("loadShedding.link.title"));
        title.getStyle().set("font-size", "1.1rem");
        title.getStyle().set("margin", "0");

        FormLayout formLayout = new FormLayout(sourceNodeCombo, targetNodeCombo, triggerStateCombo, targetActionCombo, reverseOnClearCheckbox);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("300px", 2)
        );

        HorizontalLayout actions = new HorizontalLayout(saveLinkButton, newLinkButton, clearLinkButton, deleteLinkButton);
        actions.setWrap(true);

        card.add(title, formLayout, actions);
        return card;
    }

    private Component createLinkGridCard() {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("load-shedding-section");
        card.setPadding(false);
        card.setSpacing(true);

        H2 title = new H2(t("loadShedding.link.listTitle"));
        title.getStyle().set("font-size", "1.1rem");
        title.getStyle().set("margin", "0");

        card.add(title, linkGrid);
        return card;
    }

    private void configureNodeForm() {
        nodeDeviceCombo.setItemLabelGenerator(this::deviceLabel);
        nodeDeviceCombo.setWidthFull();

        nodeChannelField.setMin(0);
        nodeChannelField.setMax(3);
        nodeChannelField.setStepButtonsVisible(true);
        nodeChannelField.setValue(0);
        nodeChannelField.setWidthFull();

        nodeXField.setMin(0);
        nodeXField.setStepButtonsVisible(true);
        nodeXField.setValue(40);
        nodeXField.setWidthFull();

        nodeYField.setMin(0);
        nodeYField.setStepButtonsVisible(true);
        nodeYField.setValue(40);
        nodeYField.setWidthFull();

        saveNodeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveNodeButton.addClickListener(event -> saveNode());
        newNodeButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        newNodeButton.addClickListener(event -> clearNodeSelection());
        clearNodeButton.addClickListener(event -> clearNodeSelection());
        deleteNodeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteNodeButton.addClickListener(event -> deleteSelectedNode());
        deleteNodeButton.setEnabled(false);
    }

    private void configureLinkForm() {
        sourceNodeCombo.setWidthFull();
        sourceNodeCombo.setItemLabelGenerator(this::nodeLabel);

        targetNodeCombo.setWidthFull();
        targetNodeCombo.setItemLabelGenerator(this::nodeLabel);

        triggerStateCombo.setItems(LoadSheddingTriggerState.values());
        triggerStateCombo.setItemLabelGenerator(trigger -> t("loadShedding.trigger." + trigger.name()));
        triggerStateCombo.setValue(LoadSheddingTriggerState.TURNED_ON);
        triggerStateCombo.setWidthFull();

        targetActionCombo.setItems(ControlAction.TURN_OFF, ControlAction.TURN_ON);
        targetActionCombo.setItemLabelGenerator(action -> t("controlAction." + action.name()));
        targetActionCombo.setValue(ControlAction.TURN_OFF);
        targetActionCombo.setWidthFull();

        reverseOnClearCheckbox.setValue(false);

        saveLinkButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveLinkButton.addClickListener(event -> saveLink());
        newLinkButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        newLinkButton.addClickListener(event -> clearLinkSelection());
        clearLinkButton.addClickListener(event -> clearLinkSelection());
        deleteLinkButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteLinkButton.addClickListener(event -> deleteSelectedLink());
        deleteLinkButton.setEnabled(false);
    }

    private void configureLinkGrid() {
        linkGrid.addColumn(link -> nodeLabel(link.getSourceNode()))
                .setHeader(t("loadShedding.grid.source"))
                .setAutoWidth(true);
        linkGrid.addColumn(link -> t("loadShedding.trigger." + link.getTriggerState().name()))
                .setHeader(t("loadShedding.grid.trigger"))
                .setAutoWidth(true);
        linkGrid.addColumn(link -> t("controlAction." + link.getTargetAction().name()))
                .setHeader(t("loadShedding.grid.action"))
                .setAutoWidth(true);
        linkGrid.addColumn(link -> link.isReverseOnClear() ? t("common.yes") : t("common.no"))
                .setHeader(t("loadShedding.grid.reverseOnClear"))
                .setAutoWidth(true);
        linkGrid.addColumn(link -> nodeLabel(link.getTargetNode()))
                .setHeader(t("loadShedding.grid.target"))
                .setAutoWidth(true);
        linkGrid.addComponentColumn(link -> {
            Button delete = new Button(t("controlTable.button.delete"), event -> {
                loadSheddingService.deleteLink(accountId, link.getId());
                if (selectedLink != null && selectedLink.getId().equals(link.getId())) {
                    clearLinkSelection();
                }
                reloadData();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return delete;
        }).setHeader(t("controlTable.grid.actions"));
        linkGrid.setAllRowsVisible(true);
        linkGrid.setWidthFull();
        linkGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        linkGrid.addItemClickListener(event -> selectLink(event.getItem()));
    }

    private void reloadData() {
        nodes = loadSheddingService.getNodes(accountId);
        links = loadSheddingService.getLinks(accountId);

        nodeDeviceCombo.setItems(standardDevices);
        sourceNodeCombo.setItems(nodes);
        targetNodeCombo.setItems(nodes);
        linkGrid.setItems(links);

        if (selectedNode != null) {
            selectedNode = nodes.stream().filter(node -> node.getId().equals(selectedNode.getId())).findFirst().orElse(null);
        }
        if (selectedLink != null) {
            selectedLink = links.stream().filter(link -> link.getId().equals(selectedLink.getId())).findFirst().orElse(null);
        }

        renderBoard();
        refreshSelections();
    }

    private void renderBoard() {
        board.removeAll();

        Element svg = new Element("svg");
        svg.setAttribute("viewBox", "0 0 " + BOARD_WIDTH + " " + BOARD_HEIGHT);
        svg.setAttribute("class", "load-shedding-svg");
        svg.appendChild(createArrowMarker());

        Map<Long, LoadSheddingNodeResponse> nodeById = nodes.stream()
                .collect(Collectors.toMap(LoadSheddingNodeResponse::getId, Function.identity()));

        for (LoadSheddingLinkResponse link : links) {
            LoadSheddingNodeResponse source = nodeById.get(link.getSourceNode().getId());
            LoadSheddingNodeResponse target = nodeById.get(link.getTargetNode().getId());
            if (source == null || target == null) {
                continue;
            }
            appendLine(svg, source, target, link);
        }

        board.getElement().appendChild(svg);

        for (LoadSheddingNodeResponse node : nodes) {
            Div nodeCard = new Div();
            nodeCard.addClassName("load-shedding-node");
            if (selectedNode != null && selectedNode.getId().equals(node.getId())) {
                nodeCard.addClassName("selected");
            }
            nodeCard.getStyle()
                    .set("left", node.getCanvasX() + "px")
                    .set("top", node.getCanvasY() + "px")
                    .set("width", NODE_WIDTH + "px")
                    .set("height", NODE_HEIGHT + "px");

            Div title = new Div();
            title.setText(node.getDevice().getDeviceName());
            title.addClassName("load-shedding-node-title");

            Div meta = new Div();
            meta.setText(t("loadShedding.node.channelLabel", node.getDeviceChannel()));
            meta.addClassName("load-shedding-node-meta");

            Div uuid = new Div();
            uuid.setText(node.getDevice().getUuid().toString());
            uuid.addClassName("load-shedding-node-uuid");

            Button delete = new Button(t("controlTable.button.delete"), event -> {
                loadSheddingService.deleteNode(accountId, node.getId());
                if (selectedNode != null && selectedNode.getId().equals(node.getId())) {
                    clearNodeSelection();
                }
                reloadData();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            delete.addClassName("load-shedding-node-delete");

            nodeCard.addClickListener(event -> selectNode(node));
            nodeCard.add(title, meta, uuid, delete);
            board.add(nodeCard);
            enableDrag(nodeCard, node);
        }
    }

    private Element createArrowMarker() {
        Element defs = new Element("defs");
        Element marker = new Element("marker");
        marker.setAttribute("id", "load-shedding-arrow");
        marker.setAttribute("markerWidth", "10");
        marker.setAttribute("markerHeight", "10");
        marker.setAttribute("refX", "9");
        marker.setAttribute("refY", "3");
        marker.setAttribute("orient", "auto");
        Element path = new Element("path");
        path.setAttribute("d", "M0,0 L0,6 L9,3 z");
        path.setAttribute("fill", "#003e4d");
        marker.appendChild(path);
        defs.appendChild(marker);
        return defs;
    }

    private void appendLine(Element svg, LoadSheddingNodeResponse source, LoadSheddingNodeResponse target, LoadSheddingLinkResponse link) {
        int x1 = source.getCanvasX() + NODE_WIDTH;
        int y1 = source.getCanvasY() + (NODE_HEIGHT / 2);
        int x2 = target.getCanvasX();
        int y2 = target.getCanvasY() + (NODE_HEIGHT / 2);

        Element line = new Element("line");
        line.setAttribute("x1", String.valueOf(x1));
        line.setAttribute("y1", String.valueOf(y1));
        line.setAttribute("x2", String.valueOf(x2));
        line.setAttribute("y2", String.valueOf(y2));
        line.setAttribute("class", "load-shedding-line");
        line.setAttribute("marker-end", "url(#load-shedding-arrow)");
        svg.appendChild(line);

        Element text = new Element("text");
        text.setAttribute("x", String.valueOf((x1 + x2) / 2));
        text.setAttribute("y", String.valueOf(((y1 + y2) / 2) - 8));
        text.setAttribute("class", "load-shedding-line-label");
        text.setText(linkLabel(link));
        svg.appendChild(text);
    }

    private void enableDrag(Div nodeCard, LoadSheddingNodeResponse node) {
        nodeCard.getElement().executeJs("""
                const element = this;
                const viewElement = $3;
                if (element.__loadSheddingDragBound) {
                  return;
                }
                        element.__loadSheddingDragBound = true;
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
                  viewElement.$server.updateNodePosition($2, nextLeft, nextTop);
                };
                element.addEventListener('pointerup', finish);
                element.addEventListener('pointercancel', finish);
                """, BOARD_WIDTH - NODE_WIDTH, BOARD_HEIGHT - NODE_HEIGHT, node.getId(), getElement());
    }

    @ClientCallable
    private void updateNodePosition(Long nodeId, Integer x, Integer y) {
        loadSheddingService.updateNodePosition(accountId, nodeId, x != null ? x : 0, y != null ? y : 0);
        reloadData();
    }

    private void saveNode() {
        try {
            DeviceResponse device = nodeDeviceCombo.getValue();
            Integer channel = nodeChannelField.getValue();
            Integer x = nodeXField.getValue();
            Integer y = nodeYField.getValue();

            if (device == null) {
                showWarning(t("loadShedding.notification.deviceRequired"));
                return;
            }
            if (channel == null) {
                showWarning(t("loadShedding.notification.channelRequired"));
                return;
            }

            LoadSheddingNodeResponse saved = loadSheddingService.saveNode(
                    accountId,
                    selectedNode != null ? selectedNode.getId() : null,
                    device.getId(),
                    channel,
                    x != null ? x : nextDefaultX(),
                    y != null ? y : nextDefaultY()
            );
            selectedNode = saved;
            Notification.show(t("loadShedding.notification.nodeSaved"))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            reloadData();
            clearNodeSelection();
        } catch (Exception ex) {
            showError(t("loadShedding.notification.failed", ex.getMessage()));
        }
    }

    private void saveLink() {
        try {
            LoadSheddingNodeResponse source = sourceNodeCombo.getValue();
            LoadSheddingNodeResponse target = targetNodeCombo.getValue();
            if (source == null || target == null) {
                showWarning(t("loadShedding.notification.linkNodesRequired"));
                return;
            }

            LoadSheddingLinkResponse saved = loadSheddingService.saveLink(
                    accountId,
                    selectedLink != null ? selectedLink.getId() : null,
                    source.getId(),
                    target.getId(),
                    triggerStateCombo.getValue(),
                    targetActionCombo.getValue(),
                    reverseOnClearCheckbox.getValue()
            );
            selectedLink = saved;
            Notification.show(t("loadShedding.notification.linkSaved"))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            reloadData();
            clearLinkSelection();
        } catch (Exception ex) {
            showError(t("loadShedding.notification.failed", ex.getMessage()));
        }
    }

    private void deleteSelectedNode() {
        if (selectedNode == null) {
            return;
        }
        loadSheddingService.deleteNode(accountId, selectedNode.getId());
        clearNodeSelection();
        reloadData();
    }

    private void deleteSelectedLink() {
        if (selectedLink == null) {
            return;
        }
        loadSheddingService.deleteLink(accountId, selectedLink.getId());
        clearLinkSelection();
        reloadData();
    }

    private void selectNode(LoadSheddingNodeResponse node) {
        selectedNode = node;
        nodeDeviceCombo.setValue(findDevice(node.getDevice().getId()));
        nodeChannelField.setValue(node.getDeviceChannel());
        nodeXField.setValue(node.getCanvasX());
        nodeYField.setValue(node.getCanvasY());
        saveNodeButton.setText(t("loadShedding.button.updateNode"));
        deleteNodeButton.setEnabled(true);
        renderBoard();
    }

    private void selectLink(LoadSheddingLinkResponse link) {
        selectedLink = link;
        sourceNodeCombo.setValue(findNode(link.getSourceNode().getId()));
        targetNodeCombo.setValue(findNode(link.getTargetNode().getId()));
        triggerStateCombo.setValue(link.getTriggerState());
        targetActionCombo.setValue(link.getTargetAction());
        reverseOnClearCheckbox.setValue(link.isReverseOnClear());
        saveLinkButton.setText(t("loadShedding.button.updateLink"));
        deleteLinkButton.setEnabled(true);
    }

    private void clearNodeSelection() {
        selectedNode = null;
        nodeDeviceCombo.clear();
        nodeChannelField.setValue(0);
        nodeXField.setValue(nextDefaultX());
        nodeYField.setValue(nextDefaultY());
        saveNodeButton.setText(t("loadShedding.button.saveNode"));
        deleteNodeButton.setEnabled(false);
        renderBoard();
    }

    private void clearLinkSelection() {
        selectedLink = null;
        sourceNodeCombo.clear();
        targetNodeCombo.clear();
        triggerStateCombo.setValue(LoadSheddingTriggerState.TURNED_ON);
        targetActionCombo.setValue(ControlAction.TURN_OFF);
        reverseOnClearCheckbox.setValue(false);
        saveLinkButton.setText(t("loadShedding.button.saveLink"));
        deleteLinkButton.setEnabled(false);
    }

    private void refreshSelections() {
        if (selectedNode != null) {
            selectNode(selectedNode);
        } else {
            clearNodeSelection();
        }

        if (selectedLink != null) {
            selectLink(selectedLink);
        } else {
            clearLinkSelection();
        }
    }

    private int nextDefaultX() {
        int index = nodes.size();
        return 40 + ((index % 4) * 260);
    }

    private int nextDefaultY() {
        int index = nodes.size();
        return 40 + ((index / 4) * 140);
    }

    private DeviceResponse findDevice(Long deviceId) {
        return standardDevices.stream()
                .filter(device -> device.getId().equals(deviceId))
                .findFirst()
                .orElse(null);
    }

    private LoadSheddingNodeResponse findNode(Long nodeId) {
        return nodes.stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    private String deviceLabel(DeviceResponse device) {
        return device.getDeviceName() + " (" + device.getUuid() + ")";
    }

    private String nodeLabel(LoadSheddingNodeResponse node) {
        return node.getDevice().getDeviceName() + " / ch " + node.getDeviceChannel();
    }

    private String linkLabel(LoadSheddingLinkResponse link) {
        String label = t("loadShedding.trigger." + link.getTriggerState().name()) + " -> " + t("controlAction." + link.getTargetAction().name());
        if (link.isReverseOnClear()) {
            label += " / " + t("loadShedding.link.reversibleShort");
        }
        return label;
    }

    private void showWarning(String message) {
        Notification.show(message).addThemeVariants(NotificationVariant.LUMO_WARNING);
    }

    private void showError(String message) {
        Notification.show(message).addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    protected String t(String key, Object... args) {
        return i18n.t(key, args);
    }

}
