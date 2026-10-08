package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DesktopCreateDialogTest {
    private final I18nService i18n = mock(I18nService.class, invocation -> invocation.getArgument(0));
    private final VerticalLayout view = new VerticalLayout();
    private DesktopCreateDialog dialog;
    private DesktopFormStateTest.ClientTextField field;
    private UI ui;

    @BeforeEach
    void setup() {
        ui = new UI();
        UI.setCurrent(ui);
        dialog = new DesktopCreateDialog(view, "Add new", i18n);
        field = new DesktopFormStateTest.ClientTextField();
        dialog.add(field);
        DesktopFormState.watch(field);
        view.add(dialog.openButton());
    }

    @AfterEach
    void cleanup() {
        UI.setCurrent(null);
    }

    @Test
    void closingRetainsDraftAndBrowserReloadMarkerUntilSaveOrRevert() {
        dialog.openButton().click();
        DesktopFormStateTest.edit(field, "draft");
        dialog.close();
        assertTrue(DesktopFormState.isDirty(view));
        assertEquals("true", dialog.getElement().getAttribute("data-desktop-dirty"));
        dialog.openButton().click();
        assertEquals("draft", field.getValue());
        DesktopFormStateTest.edit(field, "");
        assertFalse(DesktopFormState.isDirty(view));
        assertEquals("false", dialog.getElement().getAttribute("data-desktop-dirty"));
        DesktopFormStateTest.edit(field, "saved");
        dialog.savedAndClose();
        assertFalse(dialog.isOpened());
        assertFalse(DesktopFormState.isDirty(view));
        assertEquals("false", dialog.getElement().getAttribute("data-desktop-dirty"));
    }

    @Test
    void replacingDraftWaitsForDiscardAndCancelPreservesIt() {
        DesktopFormStateTest.edit(field, "draft");
        AtomicBoolean prepared = new AtomicBoolean();
        Runnable prepare = () -> {
            prepared.set(true);
            field.setValue("other record");
        };
        dialog.openPrepared("Edit", prepare);
        assertFalse(prepared.get());
        footerButton(confirmation(), "common.cancel").click();
        assertFalse(prepared.get());
        assertEquals("draft", field.getValue());
        assertTrue(DesktopFormState.isDirty(view));
        dialog.openPrepared("Edit", prepare);
        footerButton(confirmation(), "desktop.discard").click();
        assertTrue(prepared.get());
        assertTrue(dialog.isOpened());
        assertEquals("Edit", dialog.getHeaderTitle());
        assertEquals("other record", field.getValue());
        assertFalse(DesktopFormState.isDirty(view));
    }

    @Test
    void savingOneDialogFormPreservesDraftInAnotherForm() {
        var other = new DesktopFormStateTest.ClientTextField();
        dialog.add(other);
        DesktopFormState.watch(other);
        DesktopFormStateTest.edit(field, "device");
        DesktopFormStateTest.edit(other, "claim");
        DesktopFormState.saved(field);
        assertTrue(DesktopFormState.isDirty(view));
        assertEquals("true", dialog.getElement().getAttribute("data-desktop-dirty"));
        DesktopFormState.saved(other);
        assertFalse(DesktopFormState.isDirty(view));
        assertEquals("false", dialog.getElement().getAttribute("data-desktop-dirty"));
    }

    private Dialog confirmation() {
        return view.getChildren().filter(component -> component instanceof Dialog && component != dialog)
                .map(Dialog.class::cast).findFirst().orElseThrow();
    }

    private Button footerButton(Dialog target, String label) {
        return target.getFooter().getElement().getChildren().flatMap(element -> element.getComponent().stream())
                .filter(component -> component instanceof Button button && button.getText().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow();
    }
}
