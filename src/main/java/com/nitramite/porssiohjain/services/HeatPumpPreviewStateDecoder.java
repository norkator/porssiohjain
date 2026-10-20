/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */
package com.nitramite.porssiohjain.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse.State;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexDecoderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HeatPumpPreviewStateDecoder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ToshibaAcStateHexDecoderService toshibaDecoder;

    public State decode(AcType type, String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            if (type == AcType.TOSHIBA) {
                var state = toshibaDecoder.decode(raw);
                if (!state.isValid()) return null;
                String power = state.getPower().getCode();
                return new State("ON".equals(power) ? Boolean.TRUE : "OFF".equals(power) ? Boolean.FALSE : null,
                        state.getMode().getLabel(), state.getTargetTemperature().isAvailable()
                        ? state.getTargetTemperature().getTemperatureCelsius().doubleValue() : null,
                        state.getFanMode().getLabel(), state.getSwingMode().getLabel());
            }
            if (type == AcType.MITSUBISHI_MELCLOUD || type == AcType.MITSUBISHI_MELCLOUD_HOME) {
                JsonNode state = JSON.readTree(raw);
                boolean legacy = type == AcType.MITSUBISHI_MELCLOUD;
                JsonNode power = state.path(legacy ? "Power" : "power");
                JsonNode mode = state.path(legacy ? "OperationMode" : "operationMode");
                JsonNode temperature = state.path(legacy ? "SetTemperature" : "setTemperature");
                String modeLabel = mode.isMissingNode() || mode.isNull() ? null : mode.asText();
                if (legacy && mode.isInt()) {
                    modeLabel = switch (mode.intValue()) {
                        case 1 -> "Heat";
                        case 2 -> "Dry";
                        case 3 -> "Cool";
                        case 7 -> "Fan";
                        case 8 -> "Auto";
                        default -> mode.asText();
                    };
                }
                // Air-to-water zones have separate targets. Never plot one as a generic room setpoint.
                if ("AIR_TO_WATER".equals(state.path("unitType").asText())) {
                    modeLabel = text(state.path("operationModeZone1"));
                    temperature = null;
                }
                return new State(power.isBoolean() ? power.booleanValue() : null, modeLabel,
                        temperature != null && temperature.isNumber() ? temperature.doubleValue() : null,
                        text(state.path(legacy ? "SetFanSpeed" : "setFanSpeed")),
                        text(state.path(legacy ? "VaneVertical" : "vaneVerticalDirection")));
            }
        } catch (Exception ignored) {
            // A saved command may be from a different/older format. Keep it visible via state details.
        }
        return null;
    }

    private String text(JsonNode value) {
        return value.isValueNode() && !value.isNull() ? value.asText() : null;
    }
}
