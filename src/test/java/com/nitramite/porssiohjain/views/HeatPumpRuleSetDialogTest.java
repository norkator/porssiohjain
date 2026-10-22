package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.*;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse.*;
import com.nitramite.porssiohjain.views.components.HeatPumpControlPreviewChart;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.*;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeatPumpRuleSetDialogTest {
    private final ControlService controls = mock(ControlService.class);
    private final HeatPumpControlPreviewService previews = mock(HeatPumpControlPreviewService.class);
    private final HeatPumpStateDialogService states = mock(HeatPumpStateDialogService.class);
    private final DeviceResponse pump = DeviceResponse.builder().id(20L).deviceName("Living room")
            .deviceType(DeviceType.HEAT_PUMP).build();
    private HeatPumpRuleSetDialog dialog;

    @BeforeEach
    void setup() {
        UI.setCurrent(new UI());
        UI.getCurrent().setLocale(Locale.ENGLISH);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("translations/messages");
        messages.setDefaultEncoding("UTF-8");
        Instant now = Instant.parse("2026-10-22T08:00:00Z");
        when(previews.previewDraft(eq(1L), eq(10L), eq(20L), any())).thenReturn(new HeatPumpControlPreviewResponse(
                "Europe/Helsinki", now, now.minusSeconds(3600), now.plusSeconds(86400),
                List.of(new PricePeriod(now, now.plusSeconds(900), BigDecimal.ONE)),
                List.of(new Pump(20L, "Living room", false, List.of()))));
        var on = ControlHeatPumpResponse.builder().id(100L).deviceId(20L).device(pump).stateHex("on-state")
                .controlAction(ControlAction.TURN_ON).comparisonType(ComparisonType.LESS_THAN).priceLimit(BigDecimal.TEN).build();
        var off = ControlHeatPumpResponse.builder().id(200L).deviceId(20L).device(pump).stateHex("off-state")
                .controlAction(ControlAction.TURN_OFF).comparisonType(ComparisonType.GREATER_THAN).priceLimit(BigDecimal.TEN).build();
        dialog = new HeatPumpRuleSetDialog(1L, 10L, "Europe/Helsinki", 20L, List.of(pump), List.of(on, off),
                controls, previews, states, new I18nService(messages), () -> {});
    }

    @AfterEach
    void cleanup() { UI.setCurrent(null); }

    @Test
    void existingRowsBecomeOneEditorWithInlineGraphsAndIndependentActionStateCapture() {
        assertThat(descendants(dialog).filter(HeatPumpControlPreviewChart.class::isInstance)).hasSize(2);
        var capture = descendants(dialog).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals("Poll and edit settings")).toList();
        capture.get(0).click();
        capture.get(1).click();
        var fields = org.mockito.ArgumentCaptor.forClass(TextField.class);
        verify(states, times(2)).openStateDialog(eq(pump), fields.capture());
        assertThat(fields.getAllValues().get(0)).isNotSameAs(fields.getAllValues().get(1));
        assertThat(fields.getAllValues()).extracting(TextField::getValue).containsExactly("on-state", "off-state");
        verifyNoInteractions(controls);
    }

    @Test
    void changingPriceUpdatesDraftGraphWithoutSavingRulesOrApplyingCommands() {
        var price = descendants(dialog).filter(NumberField.class::isInstance).map(NumberField.class::cast)
                .filter(field -> field.getLabel().equals("Combined price (snt/kWh)")).findFirst().orElseThrow();
        price.setValue(12.5);
        var request = org.mockito.ArgumentCaptor.forClass(ControlHeatPumpRulesRequest.class);
        verify(previews, atLeast(2)).previewDraft(eq(1L), eq(10L), eq(20L), request.capture());
        assertThat(request.getValue().rules().getFirst().priceLimit()).isEqualByComparingTo("12.5");
        assertThat(descendants(dialog).filter(HeatPumpControlPreviewChart.class::isInstance)).hasSize(2);
        verifyNoInteractions(controls);
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
