package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse.*;
import com.nitramite.porssiohjain.views.components.HeatPumpControlPreviewChart;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HeatPumpControlPreviewDialogTest {
    private final Instant now = Instant.parse("2026-10-20T09:00:00Z");

    @AfterEach
    void cleanup() {
        UI.setCurrent(null);
    }

    @Test
    void emptySavedRulesShowAnEmptyStateWithoutAChart() {
        var dialog = new HeatPumpControlPreviewDialog(preview(List.of()), translations(Locale.ENGLISH), state -> {});

        assertThat(dialog.getElement().getTextRecursively()).contains("No saved heat-pump rules");
        assertThat(descendants(dialog).filter(HeatPumpControlPreviewChart.class::isInstance)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void switchingPumpAndDayKeepsOnlyRelevantPeriodsAndShowsCompetingRuleNotice() {
        var today = period(now, now.plusSeconds(900), Reason.NO_MATCH);
        Instant tomorrowStart = Instant.parse("2026-10-20T21:00:00Z");
        var tomorrow = period(tomorrowStart, tomorrowStart.plusSeconds(900), Reason.SCHEDULE);
        var dialog = new HeatPumpControlPreviewDialog(preview(List.of(
                new Pump(1L, "Living room", true, List.of(today, tomorrow)),
                new Pump(2L, "Bedroom", false, List.of(today)))), translations(Locale.ENGLISH), state -> {});

        assertThat(grid(dialog).getListDataView().getItems()).containsExactly(today);
        assertThat(dialog.getElement().getTextRecursively()).contains("Other rules linked to this pump");
        ComboBox<java.time.LocalDate> day = (ComboBox<java.time.LocalDate>) ReflectionTestUtils.getField(dialog, "day");
        day.setValue(java.time.LocalDate.of(2026, 10, 21));
        assertThat(grid(dialog).getListDataView().getItems()).containsExactly(tomorrow);
        ComboBox<Pump> pump = (ComboBox<Pump>) ReflectionTestUtils.getField(dialog, "pump");
        pump.setValue(previewPumps(dialog).getLast());
        assertThat(grid(dialog).getListDataView().getItems()).isEmpty();
        assertThat(dialog.getElement().getTextRecursively()).doesNotContain("Other rules linked to this pump");
    }

    @Test
    void finnishTranslationsIncludeMissingPriceAndDisabledPumpExplanations() {
        var dialog = new HeatPumpControlPreviewDialog(preview(List.of(new Pump(1L, "Pumppu", false,
                List.of(period(now, now.plusSeconds(900), Reason.DISABLED))))), translations(Locale.forLanguageTag("fi")), state -> {});

        assertThat(dialog.getHeaderTitle()).isEqualTo("Lämpöpumpun toiminnan esikatselu");
        assertThat(dialog.getElement().getTextRecursively()).contains("Osa hinnoista puuttuu", "poistettu käytöstä");
        assertThat(dialog.getElement().getTextRecursively()).doesNotContain("controlTable.heatPumpPreview.");
    }

    private I18nService translations(Locale locale) {
        UI.setCurrent(new UI());
        UI.getCurrent().setLocale(locale);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("translations/messages");
        messages.setDefaultEncoding("UTF-8");
        return new I18nService(messages);
    }

    private HeatPumpControlPreviewResponse preview(List<Pump> pumps) {
        return new HeatPumpControlPreviewResponse("Europe/Helsinki", now,
                Instant.parse("2026-10-19T21:00:00Z"), Instant.parse("2026-10-21T21:00:00Z"),
                List.of(new PricePeriod(now, now.plusSeconds(900), null)), pumps);
    }

    private Period period(Instant start, Instant end, Reason reason) {
        return new Period(start, end, null, reason, null, null, null, null, true);
    }

    @SuppressWarnings("unchecked")
    private Grid<Period> grid(Component dialog) {
        return (Grid<Period>) descendants(dialog).filter(Grid.class::isInstance).findFirst().orElseThrow();
    }

    private List<Pump> previewPumps(HeatPumpControlPreviewDialog dialog) {
        return ((HeatPumpControlPreviewResponse) ReflectionTestUtils.getField(dialog, "preview")).pumps();
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
