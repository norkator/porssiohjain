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

package com.nitramite.porssiohjain.services.tuya;

/** Only safe, translated error keys/codes may reach the UI; never provider payloads or tokens. */
public class TuyaException extends RuntimeException {
    private final String code;

    public TuyaException(String messageKey) {
        this(messageKey, "");
    }

    public TuyaException(String messageKey, String code) {
        super(messageKey);
        this.code = code != null && code.matches("[A-Za-z0-9_-]{1,40}") ? code : "";
    }

    public String code() { return code; }
}
