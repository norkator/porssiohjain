package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.FeatureRequestInput;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.Test;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureRequestDialogTest {
    @Test
    void wizardValidatesKeepsDraftAndSavesOnlyAfterReview() {
        UI.setCurrent(new UI());
        try (var auth = mockStatic(ViewAuthUtils.class)) {
            var authService = mock(AuthService.class);
            var service = mock(FeatureRequestService.class);
            var i18n = mock(I18nService.class, invocation -> invocation.getMethod().getName().equals("t") ? invocation.getArgument(0) : null);
            var account = AccountEntity.builder().id(9L).email("user@example.com").build();
            auth.when(() -> ViewAuthUtils.findAuthenticatedAccount(authService)).thenReturn(account);
            auth.when(() -> ViewAuthUtils.getAuthenticatedAccount(authService, "home.sessionExpired")).thenReturn(account);
            var dialog = new FeatureRequestDialog(authService, service, i18n);
            var fields = children(dialog).filter(c -> c instanceof TextArea).map(TextArea.class::cast).toList();
            var email = children(dialog).filter(c -> c instanceof EmailField).map(EmailField.class::cast).findFirst().orElseThrow();
            assertEquals("user@example.com", email.getValue());
            button(dialog, "featureRequest.next").click();
            assertTrue(fields.getFirst().isInvalid());
            fields.getFirst().setValue("My household");
            button(dialog, "featureRequest.next").click();
            fields.getLast().setValue("Improve controls");
            button(dialog, "featureRequest.back").click();
            assertEquals("My household", fields.getFirst().getValue());
            button(dialog, "featureRequest.next").click();
            button(dialog, "featureRequest.next").click();
            verifyNoInteractions(service);
            email.clear();
            button(dialog, "featureRequest.send").click();
            verify(service).submit(9L, new FeatureRequestInput("My household", "Improve controls", ""));
            assertTrue(fields.getFirst().isEmpty());
            assertTrue(fields.getLast().isEmpty());
        } finally { UI.setCurrent(null); }
    }

    private Stream<Component> children(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::children));
    }

    private Button button(FeatureRequestDialog dialog, String label) {
        return dialog.getFooter().getElement().getChildren().flatMap(element -> element.getComponent().stream()).filter(c -> c instanceof Button b && b.getText().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow();
    }
}
