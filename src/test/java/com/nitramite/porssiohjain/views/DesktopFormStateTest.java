package com.nitramite.porssiohjain.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DesktopFormStateTest {
    @Test
    void tracksEditsRevertsSuccessfulSavesAndServerResetsSeparately() {
        ClientTextField name = new ClientTextField();
        name.setValue("Original");
        ClientTextField other = new ClientTextField();
        Div form = new Div(name, other);
        DesktopFormState.watch(form);
        assertFalse(DesktopFormState.isDirty(form));
        edit(name, "Changed");
        assertTrue(DesktopFormState.isDirty(form));
        assertEquals("true", name.getElement().getAttribute("data-desktop-dirty"));
        edit(name, "Original");
        assertFalse(DesktopFormState.isDirty(form));
        edit(name, "Saved");
        edit(other, "Still unsaved");
        DesktopFormState.saved(name);
        assertTrue(DesktopFormState.isDirty(form));
        other.clear();
        assertFalse(DesktopFormState.isDirty(form));
        edit(name, "Draft");
        // A failed save does not call saved(), so the warning remains active.
        assertTrue(DesktopFormState.isDirty(form));
        DesktopFormState.saved(form);
        assertFalse(DesktopFormState.isDirty(form));
    }

    @Test
    void ignoresUnregisteredFiltersAndRemovedForms() {
        ClientTextField filter = new ClientTextField();
        ClientTextField field = new ClientTextField();
        Div view = new Div(field, filter);
        DesktopFormState.watch(field);
        edit(filter, "search");
        assertFalse(DesktopFormState.isDirty(view));
        edit(field, "draft");
        view.remove(field);
        assertFalse(DesktopFormState.isDirty(view));
    }

    static void edit(ClientTextField field, String value) {
        field.setModelValue(value, true);
    }

    static class ClientTextField extends TextField {
        @Override
        public void setModelValue(String value, boolean fromClient) {
            super.setModelValue(value, fromClient);
        }
    }
}
