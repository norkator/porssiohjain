package com.nitramite.porssiohjain.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.data.value.HasValueChangeMode;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.util.Objects;

/** Explicitly registered editable fields; filters and live controls are not drafts. */
final class DesktopFormState {
    private final HasValue<?, ?> field;
    private final Component component;
    private Object savedValue;

    private DesktopFormState(Component component, HasValue<?, ?> field) {
        this.component = component;
        this.field = field;
        savedValue = field.getValue();
        if (field instanceof HasValueChangeMode mode) mode.setValueChangeMode(ValueChangeMode.EAGER);
        field.addValueChangeListener(event -> {
            // Loading/resetting a form from the server establishes its new baseline.
            if (!event.isFromClient()) savedValue = event.getValue();
            update();
        });
    }

    static void watch(Component root) {
        if (root instanceof HasValue<?, ?> field && !field.isReadOnly()
                && ComponentUtil.getData(root, DesktopFormState.class) == null) {
            ComponentUtil.setData(root, DesktopFormState.class, new DesktopFormState(root, field));
        }
        root.getChildren().forEach(DesktopFormState::watch);
    }

    static boolean isDirty(Component root) {
        DesktopFormState state = ComponentUtil.getData(root, DesktopFormState.class);
        return Boolean.TRUE.equals(ComponentUtil.getData(root, "desktopManualDraft"))
                || state != null && state.dirty() || root.getChildren().anyMatch(DesktopFormState::isDirty);
    }

    static void markDirty(Component root) {
        ComponentUtil.setData(root, "desktopManualDraft", true);
        root.getElement().setAttribute("data-desktop-dirty", "true");
        updateDialogMarkers(root);
    }

    static void watchChanges(HasValue<?, ?> field, Component owner) {
        if (field instanceof HasValueChangeMode mode) mode.setValueChangeMode(ValueChangeMode.EAGER);
        field.addValueChangeListener(event -> {
            if (event.isFromClient()) markDirty(owner);
        });
    }

    static void saved(Component root) {
        ComponentUtil.setData(root, "desktopManualDraft", false);
        root.getElement().removeAttribute("data-desktop-dirty");
        DesktopFormState state = ComponentUtil.getData(root, DesktopFormState.class);
        if (state != null) {
            state.savedValue = state.field.getValue();
            state.update();
        }
        root.getChildren().forEach(DesktopFormState::saved);
        updateDialogMarkers(root);
    }

    private boolean dirty() {
        return !Objects.equals(savedValue, field.getValue());
    }

    private void update() {
        component.getElement().setAttribute("data-desktop-dirty", String.valueOf(dirty()));
        updateDialogMarkers(component);
    }

    private static void updateDialogMarkers(Component component) {
        // Closed dialog overlays are absent from the DOM. Keep a marker on their
        // attached host so browser reload/close still detects the retained draft.
        Component current = component;
        while (current != null) {
            if (current instanceof Dialog) {
                current.getElement().setAttribute("data-desktop-dirty", String.valueOf(isDirty(current)));
            }
            current = current.getParent().orElse(null);
        }
    }
}
