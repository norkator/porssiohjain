package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.FeatureRequestDetail;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureRequestConversationDialogTest {
    @Test
    void ownerReadsDiscussionAndCanReplyWithoutEditingTheOriginal() {
        UI.setCurrent(new UI());
        try (var accounts = mockStatic(ViewAuthUtils.class)) {
            var auth = mock(AuthService.class);
            var service = mock(FeatureRequestService.class);
            accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(AccountEntity.builder().id(9L).build());
            var detail = new FeatureRequestDetail(42L, "Original use", "Original idea", null, Instant.now(), false,
                    List.of(new FeatureRequestDetail.Comment(12L, true, "Admin reply", Instant.now())));
            when(service.detail(9L, 42L, false)).thenReturn(detail);
            var changed = mock(Runnable.class);
            var dialog = new FeatureRequestConversationDialog(auth, service, translations(), 42L, false, changed);
            dialog.open();
            verify(service).markRead(9L, 42L, 12L);
            var fields = children(dialog).filter(TextArea.class::isInstance).map(TextArea.class::cast).toList();
            assertEquals(1, fields.size());
            fields.getFirst().setValue("My reply");
            dialog.close();
            dialog.open();
            assertEquals("My reply", fields.getFirst().getValue());
            footer(dialog, "featureRequest.sendComment").click();
            verify(service).comment(9L, 42L, "My reply", false);
            assertTrue(fields.getFirst().isEmpty());
            assertTrue(children(dialog).anyMatch(c -> c instanceof com.vaadin.flow.component.html.Span span && span.getText().equals("Original idea")));
        } finally { UI.setCurrent(null); }
    }

    @Test
    void adminCanHandleAndReopenAndSupportCanReadAndReplyAsTheSelectedUser() {
        UI.setCurrent(new UI());
        try (var accounts = mockStatic(ViewAuthUtils.class)) {
            var auth = mock(AuthService.class);
            var service = mock(FeatureRequestService.class);
            accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(AccountEntity.builder().id(7L).admin(true).build());
            when(service.detail(7L, 42L, true)).thenReturn(
                    new FeatureRequestDetail(42L, "Use", "Idea", null, Instant.now(), false, List.of()),
                    new FeatureRequestDetail(42L, "Use", "Idea", null, Instant.now(), true, List.of()));
            var dialog = new FeatureRequestConversationDialog(auth, service, translations(), 42L, true, () -> {});
            dialog.open();
            footer(dialog, "featureRequest.markHandled").click();
            verify(service).setHandled(7L, 42L, true);
            footer(dialog, "featureRequest.reopen").click();
            verify(service).setHandled(7L, 42L, false);
            verify(service, never()).markRead(any(), any(), anyLong());
            accounts.when(() -> ViewAuthUtils.isImpersonating()).thenReturn(true);
            when(service.detail(7L, 42L, false)).thenReturn(new FeatureRequestDetail(42L, "Use", "Idea", null, Instant.now(), false, List.of()));
            var preview = new FeatureRequestConversationDialog(auth, service, translations(), 42L, false, () -> {});
            preview.open();
            assertTrue(footer(preview, "featureRequest.sendComment").isEnabled());
            verify(service).markRead(7L, 42L, 0L);
            children(preview).filter(TextArea.class::isInstance).map(TextArea.class::cast).findFirst().orElseThrow().setValue("Support reply");
            footer(preview, "featureRequest.sendComment").click();
            verify(service).comment(7L, 42L, "Support reply", false);
        } finally { UI.setCurrent(null); }
    }

    private I18nService translations() {
        return mock(I18nService.class, call -> call.getMethod().getName().equals("t") ? call.getArgument(0) : null);
    }
    private Button footer(com.vaadin.flow.component.dialog.Dialog dialog, String label) {
        return dialog.getFooter().getElement().getChildren().flatMap(element -> element.getComponent().stream())
                .filter(c -> c instanceof Button button && button.getText().equals(label)).map(Button.class::cast).findFirst().orElseThrow();
    }
    private Stream<Component> children(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::children));
    }
}
