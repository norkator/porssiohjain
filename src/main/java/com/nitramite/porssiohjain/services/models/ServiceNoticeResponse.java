/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */

package com.nitramite.porssiohjain.services.models;

import java.time.Instant;

public record ServiceNoticeResponse(
        boolean active,
        String text,
        Instant updatedAt
) {
}
