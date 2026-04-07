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
public class ToshibaAcStateHexEditorService {

    public static final int EXPECTED_BYTE_LENGTH = 19;
    public static final int EXPECTED_HEX_LENGTH = EXPECTED_BYTE_LENGTH * 2;
    public static final int MIN_TARGET_TEMPERATURE = 16;
    public static final int MAX_TARGET_TEMPERATURE = 30;

    public String normalizeEditableHex(String hexState) {
        if (hexState == null || hexState.isBlank()) {
            throw new IllegalArgumentException("AC state hex is empty.");
        }

        String normalized = hexState.trim().replace(" ", "").toUpperCase(Locale.ROOT);
        if ((normalized.length() % 2) != 0) {
            throw new IllegalArgumentException("AC state hex length must be even.");
        }
        if (!normalized.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException("AC state hex contains non-hex characters.");
        }
        if (normalized.length() < EXPECTED_HEX_LENGTH) {
            throw new IllegalArgumentException("AC state hex must contain at least 19 bytes.");
        }
        if (normalized.length() > EXPECTED_HEX_LENGTH) {
            normalized = normalized.substring(0, EXPECTED_HEX_LENGTH);
        }
        return normalized;
    }

    public String applyEditableSettings(String hexState, Boolean powerOn, EditableMode mode, Integer targetTemperature) {
        byte[] bytes = hexToBytes(normalizeEditableHex(hexState));

        if (powerOn != null) {
            bytes[0] = (byte) (powerOn ? 0x30 : 0x31);
        }
        if (mode != null) {
            bytes[1] = mode.rawValue();
        }
        if (targetTemperature != null) {
            if (targetTemperature < MIN_TARGET_TEMPERATURE || targetTemperature > MAX_TARGET_TEMPERATURE) {
                throw new IllegalArgumentException(
                        "Target temperature must be between %d and %d C.".formatted(
                                MIN_TARGET_TEMPERATURE,
                                MAX_TARGET_TEMPERATURE
                        )
                );
            }
            bytes[2] = (byte) targetTemperature.intValue();
        }
        return bytesToHex(bytes);
    }

    private byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02X", Byte.toUnsignedInt(value)));
        }
        return builder.toString();
    }

    public enum EditableMode {
        AUTO("Auto", "AUTO", (byte) 0x41),
        COOL("Cool", "COOL", (byte) 0x42),
        HEAT("Heat", "HEAT", (byte) 0x43),
        DRY("Dry", "DRY", (byte) 0x44),
        FAN("Fan", "FAN", (byte) 0x45);

        private final String label;
        private final String decoderCode;
        private final byte rawValue;

        EditableMode(String label, String decoderCode, byte rawValue) {
            this.label = label;
            this.decoderCode = decoderCode;
            this.rawValue = rawValue;
        }

        public String label() {
            return label;
        }

        public byte rawValue() {
            return rawValue;
        }

        public static EditableMode fromDecoderCode(String decoderCode) {
            if (decoderCode == null || decoderCode.isBlank()) {
                return null;
            }
            for (EditableMode mode : values()) {
                if (mode.decoderCode.equalsIgnoreCase(decoderCode)) {
                    return mode;
                }
            }
            return null;
        }
    }
}
