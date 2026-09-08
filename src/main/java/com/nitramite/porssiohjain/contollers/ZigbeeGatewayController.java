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
import com.nitramite.porssiohjain.services.AdminClientCallLogService;
import com.nitramite.porssiohjain.services.ZigbeeGatewaySyncService;
import com.nitramite.porssiohjain.services.ZigbeeGatewayBackupService;
import com.nitramite.porssiohjain.services.models.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/zigbee-gateways")
@RequiredArgsConstructor
public class ZigbeeGatewayController {
    private final ZigbeeGatewaySyncService syncService;
    private final ZigbeeGatewayBackupService backupService;
    private final AdminClientCallLogService deviceCallLogService;
    private final AuthContext authContext;

    @RequireAuth
    @PostMapping("/{gatewayId}/sync")
    public ZigbeeGatewaySyncResponse sync(@PathVariable UUID gatewayId,
            @RequestBody ZigbeeGatewaySyncRequest request,
            HttpServletRequest httpRequest) {
        deviceCallLogService.recordZigbeeGatewaySync(gatewayId.toString(), resolveClientIp(httpRequest));
        return syncService.sync(authContext.getAccountId(), gatewayId, request);
    }

    @RequireAuth
    @PutMapping("/backups/{coordinatorIeee}")
    public ZigbeeGatewayBackup saveBackup(@PathVariable String coordinatorIeee,
            @RequestBody ZigbeeGatewayBackup backup) {
        return backupService.save(authContext.getAccountId(), coordinatorIeee, backup);
    }

    @RequireAuth
    @GetMapping("/backups/{coordinatorIeee}")
    public ZigbeeGatewayBackup getBackup(@PathVariable String coordinatorIeee) {
        return backupService.get(authContext.getAccountId(), coordinatorIeee);
    }

    @RequireAuth
    @GetMapping("/backups")
    public java.util.List<ZigbeeGatewayBackup> listBackups() {
        return backupService.list(authContext.getAccountId());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
