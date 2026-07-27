/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */

package com.nitramite.porssiohjain.services.models;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}
