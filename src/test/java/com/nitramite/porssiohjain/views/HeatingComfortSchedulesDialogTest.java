package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerConfigurationService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeatingComfortSchedulesDialogTest {
    private final HeatingPlannerConfigurationService service = mock();
    private final Runnable onSaved = mock();
    private HeatingComfortSchedulesDialog manager;

    @BeforeEach
    void setUp() {
        UI.setCurrent(new UI());
        when(service.comfortSchedules(7L, 8L)).thenReturn(List.of());
        var i18n = mock(I18nService.class, invocation -> invocation.getArgument(0));
        manager = new HeatingComfortSchedulesDialog(service, i18n, 7L, 8L, onSaved, id -> false);
    }

    @AfterEach
    void tearDown() { UI.setCurrent(null); }

    @Test
    void createsNamedScheduleWithDefaultAndMorningWindowAndClearsOnlySavedDraft() {
        var editor = editor();
        descendants(editor).filter(TextField.class::isInstance).map(TextField.class::cast).findFirst().orElseThrow()
                .setValue("Morning comfort");
        button(editor, "addWindow").click();
        assertThat(DesktopFormState.isDirty(manager)).isTrue();
        button(editor, "save").click();

        var saved = ArgumentCaptor.forClass(HeatingPlannerConfigurationService.ComfortScheduleConfiguration.class);
        verify(service).saveComfortSchedule(eq(7L), eq(8L), saved.capture());
        assertThat(saved.getValue().name()).isEqualTo("Morning comfort");
        assertThat(saved.getValue().schedule().defaultTarget()).isEqualByComparingTo("21");
        var window = saved.getValue().schedule().periods().getFirst();
        assertThat(window.from()).isEqualTo(LocalTime.of(6, 0));
        assertThat(window.to()).isEqualTo(LocalTime.of(9, 0));
        assertThat(window.target()).isEqualByComparingTo("22");
        verify(onSaved).run();
        assertThat(editor.isOpened()).isFalse();
        assertThat(DesktopFormState.isDirty(manager)).isFalse();
    }

    @Test
    void overlappingWindowsKeepDraftAndDoNotSave() {
        var editor = editor();
        button(editor, "addWindow").click();
        button(editor, "addWindow").click();
        button(editor, "save").click();

        verify(service, never()).saveComfortSchedule(any(), any(), any());
        assertThat(editor.isOpened()).isTrue();
        assertThat(DesktopFormState.isDirty(manager)).isTrue();
        button(editor, "cancel").click();
        assertThat(DesktopFormState.isDirty(manager)).isFalse();
    }

    @Test
    void featureUsesEnglishMessagesWithoutChangingFinnishDesktopLocale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("fi"));
            var desktopUi = new UI();
            desktopUi.setLocale(java.util.Locale.forLanguageTag("fi"));
            UI.setCurrent(desktopUi);
            var messages = new org.springframework.context.support.ResourceBundleMessageSource();
            messages.setBasename("translations/messages");
            messages.setDefaultEncoding("UTF-8");
            var english = new I18nService(messages);
            var dialog = new HeatingComfortSchedulesDialog(service, english, 7L, 8L, onSaved, id -> false);
            assertThat(dialog.getHeaderTitle()).isEqualTo("Comfort schedules");
            assertThat(english.tEnglish("heatingPlanner.chart.now")).isEqualTo("Now");
            assertThat(english.t("heatingPlanner.chart.now")).isEqualTo("Nyt");
            assertThat(desktopUi.getLocale().getLanguage()).isEqualTo("fi");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    private Dialog editor() {
        button(manager, "addSchedule").click();
        return descendants(manager).filter(Dialog.class::isInstance).map(Dialog.class::cast)
                .filter(dialog -> dialog != manager).findFirst().orElseThrow();
    }

    private Button button(Component root, String key) {
        Stream<Component> footer = root instanceof Dialog dialog ? dialog.getFooter().getElement().getChildren()
                .flatMap(element -> element.getComponent().stream()) : Stream.empty();
        return Stream.concat(descendants(root), footer).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals("heatingPlanner.comfort." + key)).findFirst().orElseThrow();
    }

    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
