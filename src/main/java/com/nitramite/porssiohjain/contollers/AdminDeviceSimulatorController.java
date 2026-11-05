/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.simulation.DeviceSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/device-simulator/accounts/{accountId}")
@RequireAuth
@RequiredArgsConstructor
public class AdminDeviceSimulatorController {
    private final AuthContext auth;
    private final DeviceSimulationService simulations;

    @GetMapping("/devices")
    public List<DeviceSimulationService.AdminDevice> list(@PathVariable Long accountId) throws AccessDeniedException {
        return simulations.list(auth.getAccountId(), accountId);
    }

    @PutMapping("/devices/{deviceId}")
    public void configure(@PathVariable Long accountId, @PathVariable Long deviceId,
                          @RequestBody Configuration request) throws AccessDeniedException {
        simulations.setSimulated(auth.getAccountId(), accountId, deviceId, request.simulated());
    }

    @PostMapping("/heat-pumps")
    public Map<String, UUID> createHeatPump(@PathVariable Long accountId) throws AccessDeniedException {
        return Map.of("uuid", simulations.createHeatPump(auth.getAccountId(), accountId));
    }

    public record Configuration(boolean simulated) {}
}
