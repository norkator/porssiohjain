package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcStateService;
import com.nitramite.porssiohjain.services.mitsubishihome.MitsubishiMelCloudHomeStateService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexDecoderService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexEditorService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HeatPumpCommandLogContentTest {
    private static final String HEX = "30431541000000000000000000000000000000";
    private static final String FIELD = "controlTable.dialog.queryState.field.";
    private static final String HOME = "controlTable.dialog.queryState.melCloudHome.";
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final DeviceAcDataRepository acData = mock(DeviceAcDataRepository.class);
    private final ToshibaAcStateService toshiba = mock(ToshibaAcStateService.class);
    private final MitsubishiAcStateService mitsubishi = mock(MitsubishiAcStateService.class);
    private final MitsubishiMelCloudHomeStateService home = mock(MitsubishiMelCloudHomeStateService.class);
    private final AcCommandDispatchService dispatch = mock(AcCommandDispatchService.class);
    private final HeatPumpStateDialogService service = new HeatPumpStateDialogService(
            devices, acData, toshiba, mitsubishi, home, dispatch, new ToshibaAcStateHexDecoderService(),
            new ToshibaAcStateHexEditorService(), mock(I18nService.class, invocation -> invocation.getArgument(0)));

    @AfterEach
    void openingHistoricalCommandsNeverReadsLiveStateOrSendsCommands() {
        verifyNoInteractions(devices, acData, toshiba, mitsubishi, home, dispatch);
    }

    @Test
    void decodesPersistedToshibaAmqpPayloadAndLegacyHexFormats() {
        for (String payload : new String[]{
                "{\"sourceId\":\"test-client\",\"cmd\":\"CMD_FCU_TO_AC\",\"payload\":{\"data\":\"" + HEX + "\"}}",
                HEX,
                "SIMULATED Toshiba stateHex=" + HEX
        }) {
            Component content = service.createSentCommandInfoContent(payload);

            assertThat(field(content, FIELD + "power")).startsWith("On | ");
            assertThat(field(content, FIELD + "mode")).startsWith("Heat | ");
            assertThat(field(content, FIELD + "targetTemperature")).startsWith("21 C | ");
            assertThat(field(content, FIELD + "fanMode")).startsWith("Auto | ");
            assertReadOnly(content);
        }
    }

    @Test
    void decodesLegacyMitsubishiNumericSettings() {
        Component content = service.createSentCommandInfoContent("""
                {"Power":false,"OperationMode":3,"SetTemperature":20.5,
                 "SetFanSpeed":2,"VaneVertical":7,"VaneHorizontal":12,"EffectiveFlags":287}
                """);

        assertThat(field(content, FIELD + "power")).isEqualTo("false");
        assertThat(field(content, FIELD + "mode")).isEqualTo("Cool | 3");
        assertThat(field(content, FIELD + "targetTemperature")).isEqualTo("20.5");
        assertThat(field(content, FIELD + "fanMode")).isEqualTo("Speed 2 | 2");
        assertThat(field(content, "Vane vertical")).isEqualTo("Swing | 7");
        assertThat(field(content, "Vane horizontal")).isEqualTo("Swing | 12");
        assertReadOnly(content);
    }

    @Test
    void showsHomeAirToAirSentSettingsAndLeavesOmittedValuesBlank() {
        Component content = service.createSentCommandInfoContent("""
                {"unitType":"AIR_TO_AIR","power":true,"operationMode":"Heat","setTemperature":22.5,
                 "setFanSpeed":"Three","vaneVerticalDirection":"Swing","vaneHorizontalDirection":"Centre"}
                """);

        assertThat(field(content, FIELD + "mode")).isEqualTo("Heat");
        assertThat(field(content, FIELD + "targetTemperature")).isEqualTo("22.5");
        assertThat(field(content, FIELD + "fanMode")).isEqualTo("Three");
        assertThat(field(content, HOME + "verticalVane")).isEqualTo("Swing");
        assertThat(field(content, HOME + "horizontalVane")).isEqualTo("Centre");
        assertThat(field(content, HOME + "standby")).isEmpty();
        assertThat(field(content, FIELD + "indoorTemperature")).isEmpty();
        assertReadOnly(content);
    }

    @Test
    void showsHomeAirToWaterZoneAndHotWaterSettings() {
        Component content = service.createSentCommandInfoContent("""
                {"unitType":"AIR_TO_WATER","power":true,"inStandbyMode":false,
                 "operationModeZone1":"HeatCurve","setTemperatureZone1":21,
                 "setHeatFlowTemperatureZone1":35,"setCoolFlowTemperatureZone1":18,
                 "operationModeZone2":"HeatRoomTemperature","setTemperatureZone2":20,
                 "setHeatFlowTemperatureZone2":32,"setCoolFlowTemperatureZone2":19,
                 "setTankWaterTemperature":50,"forcedHotWaterMode":true}
                """);

        assertThat(field(content, HOME + "zone1Mode")).isEqualTo("HeatCurve");
        assertThat(field(content, HOME + "zone1Temperature")).isEqualTo("21.0");
        assertThat(field(content, HOME + "zone1HeatFlow")).isEqualTo("35.0");
        assertThat(field(content, HOME + "zone1CoolFlow")).isEqualTo("18.0");
        assertThat(field(content, HOME + "zone2Mode")).isEqualTo("HeatRoomTemperature");
        assertThat(field(content, HOME + "zone2Temperature")).isEqualTo("20.0");
        assertThat(field(content, HOME + "zone2HeatFlow")).isEqualTo("32.0");
        assertThat(field(content, HOME + "zone2CoolFlow")).isEqualTo("19.0");
        assertThat(field(content, HOME + "tankTemperature")).isEqualTo("50.0");
        assertThat(field(content, HOME + "forcedHotWater")).isEqualTo("true");
        assertReadOnly(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "{}", "{\"payload\":{\"data\":null}}", "{\"unitType\":\"unknown\"}"})
    void unsupportedJsonShowsDecodedUnavailable(String payload) {
        Component content = service.createSentCommandInfoContent(payload);

        assertThat(content).isInstanceOf(Paragraph.class);
        assertThat(((Paragraph) content).getText()).isEqualTo("controlTable.dialog.queryState.decodedUnavailable");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "bad hex", "3043"})
    void missingOrMalformedHexShowsWarnings(String payload) {
        Component content = service.createSentCommandInfoContent(payload);

        assertThat(field(content, FIELD + "valid")).isEqualTo("false");
        TextArea warnings = descendants(content).filter(TextArea.class::isInstance).map(TextArea.class::cast)
                .filter(area -> area.getLabel().equals(FIELD + "warnings")).findFirst().orElseThrow();
        assertThat(warnings.getValue()).isNotBlank();
        assertReadOnly(content);
    }

    private String field(Component content, String label) {
        return descendants(content).filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(field -> field.getLabel().equals(label)).findFirst().orElseThrow().getValue();
    }

    private void assertReadOnly(Component content) {
        assertThat(descendants(content).anyMatch(Button.class::isInstance)).isFalse();
        descendants(content).filter(TextField.class::isInstance).map(TextField.class::cast)
                .forEach(field -> assertThat(field.isReadOnly()).isTrue());
        descendants(content).filter(TextArea.class::isInstance).map(TextArea.class::cast)
                .forEach(area -> assertThat(area.isReadOnly()).isTrue());
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
