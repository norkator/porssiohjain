package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.FeatureRequestEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.FeatureRequestService;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminFeatureRequestsViewTest {
    private final AuthService auth = mock(AuthService.class);
    private final FeatureRequestService service = mock(FeatureRequestService.class);
    private final FeatureRequestEntity row = FeatureRequestEntity.builder().id(42L).accountId(9L).build();
    private MockedStatic<ViewAuthUtils> accounts;
    private AdminFeatureRequestsView view;
    private UI ui;

    @BeforeEach
    void setup() {
        ui = new UI();
        UI.setCurrent(ui);
        accounts = mockStatic(ViewAuthUtils.class);
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth))
                .thenReturn(AccountEntity.builder().id(7L).admin(true).build());
        when(service.listForAdmin(7L, 0)).thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 30), 31));
        var i18n = mock(I18nService.class, invocation -> invocation.getMethod().getName().equals("t")
                ? invocation.getArgument(0) : null);
        view = new AdminFeatureRequestsView(auth, service, i18n);
    }

    @AfterEach
    void cleanup() {
        accounts.close();
        UI.setCurrent(null);
    }

    @Test
    void deletingRequiresConfirmationAndCancelKeepsSubmission() {
        deleteFromGrid();
        verify(service, never()).deleteForAdmin(any(), any());
        Dialog dialog = confirmation();
        footerButton(dialog, "common.cancel").click();
        assertFalse(dialog.isOpened());
        verify(service, never()).deleteForAdmin(any(), any());
    }

    @Test
    void deletingLastSubmissionOnLastPageReturnsToPreviousPage() {
        when(service.listForAdmin(7L, 1))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 30), 31))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 30), 30));
        button(view, "featureRequest.next").click();
        deleteFromGrid();
        Dialog dialog = confirmation();
        footerButton(dialog, "admin.featureRequests.delete").click();
        verify(service).deleteForAdmin(7L, 42L);
        verify(service, times(2)).listForAdmin(7L, 0);
        assertFalse(dialog.isOpened());
        assertFalse(button(view, "featureRequest.back").isEnabled());
    }

    @Test
    void confirmingUsesCurrentAccountAndKeepsDialogOpenWhenRejected() {
        deleteFromGrid();
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth))
                .thenReturn(AccountEntity.builder().id(9L).build());
        doThrow(new IllegalArgumentException("Admin access required")).when(service).deleteForAdmin(9L, 42L);
        Dialog dialog = confirmation();
        footerButton(dialog, "admin.featureRequests.delete").click();
        verify(service).deleteForAdmin(9L, 42L);
        assertTrue(dialog.isOpened());
        verify(service, times(1)).listForAdmin(7L, 0);
    }

    @SuppressWarnings("unchecked")
    private void deleteFromGrid() {
        Grid<FeatureRequestEntity> grid = children(view).filter(Grid.class::isInstance)
                .map(component -> (Grid<FeatureRequestEntity>) component).findFirst().orElseThrow();
        var renderer = (ComponentRenderer<?, FeatureRequestEntity>) grid.getColumns().getLast().getRenderer();
        button(renderer.createComponent(row), "admin.featureRequests.delete").click();
    }

    private Dialog confirmation() {
        return view.getChildren().filter(Dialog.class::isInstance).map(Dialog.class::cast).findFirst().orElseThrow();
    }

    private Button footerButton(Dialog dialog, String label) {
        return dialog.getFooter().getElement().getChildren().flatMap(element -> element.getComponent().stream())
                .filter(component -> component instanceof Button button && button.getText().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    private Button button(Component root, String label) {
        return children(root).filter(component -> component instanceof Button button && button.getText().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    private Stream<Component> children(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::children));
    }
}
