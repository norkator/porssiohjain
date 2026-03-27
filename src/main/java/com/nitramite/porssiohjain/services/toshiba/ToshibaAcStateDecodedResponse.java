/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
