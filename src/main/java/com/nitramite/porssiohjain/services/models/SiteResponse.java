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

package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.SiteType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SiteResponse {
    private Long id;
    private String name;
    private SiteType type;
    private Boolean enabled;
    private String weatherPlace;
    private Instant createdAt;
    private Instant updatedAt;
}
