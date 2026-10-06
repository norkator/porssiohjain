package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/** A retained form shared by the list's Add button and its desktop File menu. */
final class DesktopCreateDialog extends Dialog {
    private final VerticalLayout owner;
    private final I18nService i18n;
    private final Button openButton;
    private final Span limitInfo = new Span();
    private Runnable createAction = this::open;

    DesktopCreateDialog(VerticalLayout owner, String title, I18nService i18n) {
        this.owner = owner;
        this.i18n = i18n;
        setHeaderTitle(title);
        setWidth("min(960px, 94vw)");
        setMaxHeight("90vh");
        setCloseOnOutsideClick(false);
        limitInfo.setVisible(false);
        add(limitInfo);
        getFooter().add(new Button(i18n.t("common.close"), event -> close()));
        openButton = new Button(title, VaadinIcon.PLUS.create(), event -> createAction.run());
        openButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        openButton.getStyle().set("align-self", "flex-start");
        owner.addClassName("desktop-list-view");
        owner.add(this);
        ComponentUtil.setData(owner, DesktopCreateDialog.class, this);
    }

    void onCreate(Runnable action) {
        createAction = action;
    }

    void setLimitInfo(String text, boolean reached) {
        limitInfo.setText(text);
        limitInfo.setVisible(true);
        limitInfo.getElement().getThemeList().set("badge", true);
        limitInfo.getElement().getThemeList().set("error", reached);
    }

    Button openButton() {
        return openButton;
    }

    static Button creationButton(Component view) {
        DesktopCreateDialog dialog = ComponentUtil.getData(view, DesktopCreateDialog.class);
        return dialog == null ? null : dialog.openButton();
    }

    void savedAndClose() {
        DesktopFormState.saved(this);
        close();
    }

    /** Replacing a closed dialog's draft requires the same explicit discard as navigation. */
    void openPrepared(String title, Runnable prepare) {
        Runnable proceed = () -> {
            prepare.run();
            DesktopFormState.saved(this);
            setHeaderTitle(title);
            open();
        };
        if (!DesktopFormState.isDirty(this)) {
            proceed.run();
            return;
        }
        Dialog confirmation = new Dialog();
        owner.add(confirmation);
        confirmation.setHeaderTitle(i18n.t("desktop.unsavedTitle"));
        confirmation.setCloseOnOutsideClick(false);
        confirmation.add(new Span(i18n.t("desktop.unsavedMessage")));
        confirmation.getFooter().add(
                new Button(i18n.t("common.cancel"), event -> confirmation.close()),
                new Button(i18n.t("desktop.discard"), event -> {
                    confirmation.close();
                    proceed.run();
                }));
        confirmation.addOpenedChangeListener(event -> {
            if (!event.isOpened()) owner.remove(confirmation);
        });
        confirmation.open();
    }
}
