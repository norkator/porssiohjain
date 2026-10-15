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

import java.time.Instant;

public record TuyaSession(String userCode, String endpoint, String terminalId, String userId,
                          String accessToken, String refreshToken, Instant expiresAt) {
    @Override
    public String toString() { return "TuyaSession[redacted]"; }
}
