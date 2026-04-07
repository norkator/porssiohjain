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

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ToshibaAcStateHexDecoderService {

    private static final int EXPECTED_HEX_LENGTH = 38;
    private static final int EXPECTED_BYTE_LENGTH = 19;

    public ToshibaAcStateDecodedResponse decode(String hexState) {
        ToshibaAcStateDecodedResponse response = new ToshibaAcStateDecodedResponse();
        response.setRawHex(hexState);

        if (hexState == null || hexState.isBlank()) {
            response.getWarnings().add("AC state hex is empty.");
            return response;
        }

        String normalizedHex = hexState.trim().replace(" ", "").toUpperCase(Locale.ROOT);
        response.setNormalizedHex(normalizedHex);

        if ((normalizedHex.length() % 2) != 0) {
            response.getWarnings().add("AC state hex length must be even.");
            return response;
        }

        if (!normalizedHex.matches("[0-9A-F]+")) {
            response.getWarnings().add("AC state hex contains non-hex characters.");
            return response;
        }

        if (normalizedHex.length() < EXPECTED_HEX_LENGTH) {
            response.getWarnings().add("AC state hex is shorter than the 19-byte Toshiba sample format.");
            return response;
        }

        if (normalizedHex.length() > EXPECTED_HEX_LENGTH) {
            response.getWarnings().add("AC state hex contains extra trailing bytes; only the first 19 bytes were decoded.");
            normalizedHex = normalizedHex.substring(0, EXPECTED_HEX_LENGTH);
            response.setNormalizedHex(normalizedHex);
        }

        byte[] bytes = hexToBytes(normalizedHex);
        response.setByteLength(bytes.length);
        populateRawBytes(response, bytes);

        if (bytes.length != EXPECTED_BYTE_LENGTH) {
            response.getWarnings().add("Decoded byte length did not match the expected 19-byte Toshiba sample format.");
            return response;
        }

        response.setPower(decodeValue(
                "power",
                bytes[0],
                mapStatus(bytes[0]),
                "ON",
                "OFF"
        ));
        response.setMode(decodeValue(
                "mode",
                bytes[1],
                mapMode(bytes[1]),
                null,
                null
        ));
        response.setTargetTemperature(decodeTemperature("targetTemperature", bytes[2]));
        response.setFanMode(decodeValue(
                "fanMode",
                bytes[3],
                mapFanMode(bytes[3]),
                null,
                null
        ));
        response.setSwingMode(decodeValue(
                "swingMode",
                bytes[4],
                mapSwingMode(bytes[4]),
                null,
                null
        ));
        response.setPowerSelection(decodeValue(
                "powerSelection",
                bytes[5],
                mapPowerSelection(bytes[5]),
                null,
                null
        ));

        int combinedMerit = Byte.toUnsignedInt(bytes[6]);
        response.setMeritB(decodeNibbleValue("meritB", (combinedMerit >> 4) & 0x0F, mapMeritB((combinedMerit >> 4) & 0x0F)));
        response.setMeritA(decodeNibbleValue("meritA", combinedMerit & 0x0F, mapMeritA(combinedMerit & 0x0F)));

        response.setAirPureIon(decodeValue(
                "airPureIon",
                bytes[7],
                mapAirPureIon(bytes[7]),
                null,
                null
        ));
        response.setIndoorTemperature(decodeTemperature("indoorTemperature", bytes[8]));
        response.setOutdoorTemperature(decodeTemperature("outdoorTemperature", bytes[9]));
        response.setSelfCleaning(decodeValue(
                "selfCleaning",
                bytes[14],
                mapSelfCleaning(bytes[14]),
                null,
                null
        ));

        addUnknownField(response, "reserved10", 10, bytes[10], "Meaning not confirmed by the bundled sample.");
        addUnknownField(response, "reserved11", 11, bytes[11], "Meaning not confirmed by the bundled sample.");
        addUnknownField(response, "reserved12", 12, bytes[12], "Meaning not confirmed by the bundled sample.");
        addUnknownField(response, "reserved13", 13, bytes[13], "Meaning not confirmed by the bundled sample.");
        addUnknownField(response, "reserved15", 15, bytes[15], "Meaning not confirmed by the bundled sample.");
        addUnknownField(response, "reserved16", 16, bytes[16], "Meaning not confirmed by the bundled sample.");
        addUnknownField(response, "reserved17", 17, bytes[17], "Meaning not confirmed by the bundled sample.");
        addUnknownField(response, "reserved18", 18, bytes[18], "Meaning not confirmed by the bundled sample.");

        response.setValid(true);
        response.setSummary(buildSummary(response));
        return response;
    }

    private void populateRawBytes(ToshibaAcStateDecodedResponse response, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            ToshibaAcStateDecodedResponse.RawByteValue rawByteValue = new ToshibaAcStateDecodedResponse.RawByteValue();
            rawByteValue.setIndex(i);
            rawByteValue.setRawHex(toHex(bytes[i]));
            rawByteValue.setRawUnsigned(Byte.toUnsignedInt(bytes[i]));
            rawByteValue.setRawSigned((int) bytes[i]);
            rawByteValue.setMeaning(switch (i) {
                case 0 -> "Power status";
                case 1 -> "Operating mode";
                case 2 -> "Target temperature";
                case 3 -> "Fan mode";
                case 4 -> "Swing mode";
                case 5 -> "Power selection";
                case 6 -> "Merit features: high nibble = Merit B, low nibble = Merit A";
                case 7 -> "Air pure ion";
                case 8 -> "Indoor temperature";
                case 9 -> "Outdoor temperature";
                case 14 -> "Self-cleaning";
                default -> "Unknown / undocumented field in current sample";
            });
            response.getRawBytes().add(rawByteValue);
        }
    }

    private void addUnknownField(ToshibaAcStateDecodedResponse response, String field, int index, byte raw, String note) {
        ToshibaAcStateDecodedResponse.UnknownFieldValue unknownFieldValue = new ToshibaAcStateDecodedResponse.UnknownFieldValue();
        unknownFieldValue.setField(field);
        unknownFieldValue.setIndex(index);
        unknownFieldValue.setRawHex(toHex(raw));
        unknownFieldValue.setRawUnsigned(Byte.toUnsignedInt(raw));
        unknownFieldValue.setRawSigned((int) raw);
        unknownFieldValue.setNote(note);
        response.getUnknownFields().add(unknownFieldValue);
    }

    private ToshibaAcStateDecodedResponse.DecodedValue decodeValue(
            String field,
            byte raw,
            String label,
            String onCode,
            String offCode
    ) {
        ToshibaAcStateDecodedResponse.DecodedValue value = new ToshibaAcStateDecodedResponse.DecodedValue();
        value.setField(field);
        value.setRawHex(toHex(raw));
        value.setRawUnsigned(Byte.toUnsignedInt(raw));
        value.setRawSigned((int) raw);
        value.setLabel(label != null ? label : unknownByteLabel(raw));
        value.setKnown(label != null);

        if (label == null) {
            value.setCode("UNKNOWN");
        } else if ("On".equals(label) && onCode != null) {
            value.setCode(onCode);
        } else if ("Off".equals(label) && offCode != null) {
            value.setCode(offCode);
        } else {
            value.setCode(toCode(label));
        }
        return value;
    }

    private ToshibaAcStateDecodedResponse.DecodedValue decodeNibbleValue(String field, int raw, String label) {
        ToshibaAcStateDecodedResponse.DecodedValue value = new ToshibaAcStateDecodedResponse.DecodedValue();
        value.setField(field);
        value.setRawHex(String.format("0x%X", raw));
        value.setRawUnsigned(raw);
        value.setRawSigned(raw);
        value.setLabel(label != null ? label : "Unknown value");
        value.setKnown(label != null);
        value.setCode(label != null ? toCode(label) : "UNKNOWN");
        return value;
    }

    private ToshibaAcStateDecodedResponse.TemperatureValue decodeTemperature(String field, byte raw) {
        ToshibaAcStateDecodedResponse.TemperatureValue value = new ToshibaAcStateDecodedResponse.TemperatureValue();
        int signedRaw = raw;
        value.setField(field);
        value.setRawHex(toHex(raw));
        value.setRawUnsigned(Byte.toUnsignedInt(raw));
        value.setRawSigned(signedRaw);

        Integer temperature = switch (signedRaw) {
            case 127, -128, -1 -> null;
            case 126 -> -1;
            default -> signedRaw;
        };

        value.setTemperatureCelsius(temperature);
        value.setAvailable(temperature != null);
        value.setLabel(temperature != null ? temperature + " C" : "Not reported");
        return value;
    }

    private String mapStatus(byte raw) {
        return switch (Byte.toUnsignedInt(raw)) {
            case 0x30 -> "On";
            case 0x31 -> "Off";
            case 0x02, 0xFF -> "Not set";
            default -> null;
        };
    }

    private String mapMode(byte raw) {
        return switch (Byte.toUnsignedInt(raw)) {
            case 0x41 -> "Auto";
            case 0x42 -> "Cool";
            case 0x43 -> "Heat";
            case 0x44 -> "Dry";
            case 0x45 -> "Fan";
            case 0x00, 0xFF -> "Not set";
            default -> null;
        };
    }

    private String mapFanMode(byte raw) {
        return switch (Byte.toUnsignedInt(raw)) {
            case 0x41 -> "Auto";
            case 0x31 -> "Quiet";
            case 0x32 -> "Low";
            case 0x33 -> "Medium low";
            case 0x34 -> "Medium";
            case 0x35 -> "Medium high";
            case 0x36 -> "High";
            case 0x00, 0xFF -> "Not set";
            default -> null;
        };
    }

    private String mapSwingMode(byte raw) {
        return switch (Byte.toUnsignedInt(raw)) {
            case 0x31 -> "Off";
            case 0x41 -> "Vertical swing";
            case 0x42 -> "Horizontal swing";
            case 0x43 -> "Vertical and horizontal swing";
            case 0x50 -> "Fixed 1";
            case 0x51 -> "Fixed 2";
            case 0x52 -> "Fixed 3";
            case 0x53 -> "Fixed 4";
            case 0x54 -> "Fixed 5";
            case 0x00, 0xFF -> "Not set";
            default -> null;
        };
    }

    private String mapPowerSelection(byte raw) {
        return switch (Byte.toUnsignedInt(raw)) {
            case 0x32 -> "50% power";
            case 0x4B -> "75% power";
            case 0x64 -> "100% power";
            case 0xFF -> "Not set";
            default -> null;
        };
    }

    private String mapMeritB(int raw) {
        return switch (raw) {
            case 0x00, 0x01 -> "Off";
            case 0x02 -> "Fireplace 1";
            case 0x03 -> "Fireplace 2";
            case 0x0F -> "Not set";
            default -> null;
        };
    }

    private String mapMeritA(int raw) {
        return switch (raw) {
            case 0x00 -> "Off";
            case 0x01 -> "High power";
            case 0x02 -> "CDU silent 1";
            case 0x03 -> "Eco";
            case 0x04 -> "Heating 8 C";
            case 0x05 -> "Sleep care";
            case 0x06 -> "Floor";
            case 0x07 -> "Comfort";
            case 0x0A -> "CDU silent 2";
            case 0x0F -> "Not set";
            default -> null;
        };
    }

    private String mapAirPureIon(byte raw) {
        return switch (Byte.toUnsignedInt(raw)) {
            case 0x18 -> "On";
            case 0x10 -> "Off";
            case 0xFF -> "Not set";
            default -> null;
        };
    }

    private String mapSelfCleaning(byte raw) {
        return switch (Byte.toUnsignedInt(raw)) {
            case 0x18 -> "On";
            case 0x10 -> "Off";
            case 0xFF -> "Not set";
            default -> null;
        };
    }

    private String buildSummary(ToshibaAcStateDecodedResponse response) {
        String power = response.getPower() != null ? response.getPower().getLabel() : "Unknown";
        String mode = response.getMode() != null ? response.getMode().getLabel() : "Unknown";
        String target = response.getTargetTemperature() != null ? response.getTargetTemperature().getLabel() : "Not reported";
        String fan = response.getFanMode() != null ? response.getFanMode().getLabel() : "Unknown";
        String swing = response.getSwingMode() != null ? response.getSwingMode().getLabel() : "Unknown";
        return String.format(
                "Power %s, mode %s, target %s, fan %s, swing %s.",
                power,
                mode,
                target,
                fan,
                swing
        );
    }

    private String toCode(String label) {
        return label.toUpperCase(Locale.ROOT)
                .replace("%", "PERCENT")
                .replace("+", "_PLUS_")
                .replace("-", "_")
                .replace("/", "_")
                .replace(" ", "_");
    }

    private String unknownByteLabel(byte raw) {
        return "Unknown value (" + toHex(raw) + ")";
    }

    private String toHex(byte value) {
        return String.format("0x%02X", Byte.toUnsignedInt(value));
    }

    private byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }
}
