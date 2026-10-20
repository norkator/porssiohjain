package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexDecoderService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeatPumpPreviewStateDecoderTest {
    private final HeatPumpPreviewStateDecoder decoder = new HeatPumpPreviewStateDecoder(new ToshibaAcStateHexDecoderService());

    @Test
    void decodesToshibaPowerModeTemperatureAndFan() {
        var state = decoder.decode(AcType.TOSHIBA, "30431541000000000000000000000000000000");

        assertThat(state.power()).isTrue();
        assertThat(state.temperature()).isEqualTo(21);
        assertThat(state.mode()).isEqualTo("Heat");
        assertThat(state.fan()).isEqualTo("Auto");
        assertThat(decoder.decode(AcType.TOSHIBA, "31431541000000000000000000000000000000").power()).isFalse();
        assertThat(decoder.decode(AcType.TOSHIBA, "ff431541000000000000000000000000000000").power()).isNull();
    }

    @Test
    void decodesBothMitsubishiFormatsAndLeavesAbsentFieldsUnknown() {
        var legacy = decoder.decode(AcType.MITSUBISHI_MELCLOUD,
                "{\"Power\":false,\"OperationMode\":3,\"SetTemperature\":20.5,\"SetFanSpeed\":2}");
        assertThat(legacy.power()).isFalse();
        assertThat(legacy.mode()).isEqualTo("Cool");
        assertThat(legacy.temperature()).isEqualTo(20.5);
        assertThat(legacy.fan()).isEqualTo("2");
        var home = decoder.decode(AcType.MITSUBISHI_MELCLOUD_HOME,
                "{\"power\":true,\"operationMode\":\"Heat\",\"setTemperature\":22}");
        assertThat(home.power()).isTrue();
        assertThat(home.temperature()).isEqualTo(22);
        assertThat(decoder.decode(AcType.MITSUBISHI_MELCLOUD_HOME, "{}").power()).isNull();
        assertThat(decoder.decode(AcType.MITSUBISHI_MELCLOUD_HOME, "{\"unitType\":\"AIR_TO_WATER\","
                + "\"power\":true,\"operationModeZone1\":\"HeatCurve\",\"setTemperatureZone1\":40}").temperature()).isNull();
    }

    @Test
    void invalidAndUnsupportedSavedStatesRemainUnknown() {
        assertThat(decoder.decode(AcType.TOSHIBA, "invalid")).isNull();
        assertThat(decoder.decode(AcType.MITSUBISHI_MELCLOUD, "not-json")).isNull();
        assertThat(decoder.decode(AcType.NONE, "{}")).isNull();
        assertThat(decoder.decode(AcType.TOSHIBA, null)).isNull();
    }
}
