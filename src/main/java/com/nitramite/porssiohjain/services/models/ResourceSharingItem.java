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

import com.nitramite.porssiohjain.entity.enums.ResourceType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceSharingItem {
    private Long id;
    private String name;
    private ResourceType resourceType;
    private Long resourceId;
    @Builder.Default
    private boolean shared = false;
}