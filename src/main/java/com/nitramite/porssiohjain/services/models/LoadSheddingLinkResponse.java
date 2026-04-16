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

import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.LoadSheddingTriggerState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadSheddingLinkResponse {
    private Long id;
    private LoadSheddingNodeResponse sourceNode;
    private LoadSheddingNodeResponse targetNode;
    private LoadSheddingTriggerState triggerState;
    private ControlAction targetAction;
    private boolean reverseOnClear;
    private Instant createdAt;
    private Instant updatedAt;
}
