/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.services.simulation.DeviceSimulationService;
import com.nitramite.porssiohjain.services.simulation.DeviceSimulatorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class DeviceSimulatorController {
    private final DeviceSimulatorProperties properties;
    private final DeviceSimulationService simulations;

    @GetMapping("/internal/device-simulator/devices")
    public List<DeviceSimulationService.SimulatedDevice> inventory(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (properties.getToken().isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        String expected = "Bearer " + properties.getToken();
        if (authorization == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return simulations.inventory();
    }
}
