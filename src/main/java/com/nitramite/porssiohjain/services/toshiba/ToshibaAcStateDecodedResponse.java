/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services.toshiba;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ToshibaAcStateDecodedResponse {

    private String rawHex;
    private String normalizedHex;
    private boolean valid;
    private Integer byteLength;
    private String summary;

    private DecodedValue power;
    private DecodedValue mode;
    private TemperatureValue targetTemperature;
    private DecodedValue fanMode;
    private DecodedValue swingMode;
    private DecodedValue powerSelection;
    private DecodedValue meritB;
    private DecodedValue meritA;
    private DecodedValue airPureIon;
    private TemperatureValue indoorTemperature;
    private TemperatureValue outdoorTemperature;
    private DecodedValue selfCleaning;

    private List<RawByteValue> rawBytes = new ArrayList<>();
    private List<UnknownFieldValue> unknownFields = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class DecodedValue {
        private String field;
        private String rawHex;
        private Integer rawUnsigned;
        private Integer rawSigned;
        private String code;
        private String label;
        private boolean known;
    }

    @Data
    public static class TemperatureValue {
        private String field;
        private String rawHex;
        private Integer rawUnsigned;
        private Integer rawSigned;
        private Integer temperatureCelsius;
        private boolean available;
        private String label;
    }

    @Data
    public static class RawByteValue {
        private Integer index;
        private String rawHex;
        private Integer rawUnsigned;
        private Integer rawSigned;
        private String meaning;
    }

    @Data
    public static class UnknownFieldValue {
        private String field;
        private Integer index;
        private String rawHex;
        private Integer rawUnsigned;
        private Integer rawSigned;
        private String note;
    }
}
