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

package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.ZigbeeGatewaySyncService;
import com.nitramite.porssiohjain.services.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/zigbee-gateways")
@RequiredArgsConstructor
public class ZigbeeGatewayController {
    private final ZigbeeGatewaySyncService syncService;
    private final AuthContext authContext;

    @RequireAuth
    @PostMapping("/{gatewayId}/sync")
    public ZigbeeGatewaySyncResponse sync(@PathVariable UUID gatewayId,
            @RequestBody ZigbeeGatewaySyncRequest request) {
        return syncService.sync(authContext.getAccountId(), gatewayId, request);
    }
}
