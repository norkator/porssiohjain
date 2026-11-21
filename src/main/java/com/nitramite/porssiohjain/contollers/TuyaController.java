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
import com.nitramite.porssiohjain.services.tuya.TuyaService;
import com.nitramite.porssiohjain.services.tuya.TuyaService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tuya")
@RequiredArgsConstructor
@RequireAuth
public class TuyaController {
    private final AuthContext authContext;
    private final TuyaService tuyaService;

    @GetMapping("/connections")
    public List<ConnectionStatus> listConnections() {
        return tuyaService.listConnections(authContext.getAccountId());
    }

    @PatchMapping("/connections/{connectionId}")
    public ConnectionStatus renameConnection(@PathVariable Long connectionId, @RequestBody RenameRequest request) {
        return tuyaService.renameConnection(authContext.getAccountId(), connectionId, request.label());
    }

    @DeleteMapping("/connections/{connectionId}/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable Long connectionId) {
        tuyaService.disconnect(authContext.getAccountId(), connectionId);
    }

    @PostMapping("/login")
    public ResponseEntity<QrLogin> beginLogin(@RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(tuyaService.beginLogin(authContext.getAccountId(), request.connectionId(),
                        request.label(), request.userCode()));
    }

    @PostMapping("/login/{attemptId}/complete")
    public ConnectionStatus finishLogin(@PathVariable UUID attemptId) {
        return tuyaService.finishLogin(authContext.getAccountId(), attemptId);
    }

    @DeleteMapping("/login/{attemptId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelLogin(@PathVariable UUID attemptId) {
        tuyaService.cancelLogin(authContext.getAccountId(), attemptId);
    }

    @GetMapping("/connections/{connectionId}/homes")
    public List<Home> listHomes(@PathVariable Long connectionId) {
        return tuyaService.homes(authContext.getAccountId(), connectionId);
    }

    @GetMapping("/connections/{connectionId}/homes/{homeId}/devices")
    public List<CloudDevice> listDevices(@PathVariable Long connectionId, @PathVariable String homeId) {
        return tuyaService.devices(authContext.getAccountId(), connectionId, homeId);
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult importDevice(@RequestBody ImportRequest request) {
        Long deviceId = tuyaService.importDevice(authContext.getAccountId(), request.connectionId(),
                request.externalId(), request.name(), request.timezone(), !Boolean.FALSE.equals(request.enabled()));
        return new ImportResult(deviceId);
    }

    @GetMapping("/devices/{deviceId}/connection")
    public ConnectionStatus deviceConnection(@PathVariable Long deviceId) {
        return tuyaService.deviceConnection(authContext.getAccountId(), deviceId);
    }

    @PostMapping("/devices/{deviceId}/poll")
    public Snapshot pollDevice(@PathVariable Long deviceId) {
        return tuyaService.poll(authContext.getAccountId(), deviceId);
    }

    @PostMapping("/devices/{deviceId}/relays/{code}")
    public Snapshot switchRelay(@PathVariable Long deviceId, @PathVariable String code, @RequestBody RelayRequest request) {
        if (request.on() == null) throw new IllegalArgumentException("A relay state is required");
        return tuyaService.switchRelay(authContext.getAccountId(), deviceId, code, request.on());
    }

    public record RenameRequest(String label) { }
    public record LoginRequest(Long connectionId, String label, String userCode) {
        @Override public String toString() { return "LoginRequest[redacted]"; }
    }
    public record ImportRequest(Long connectionId, String externalId, String name, String timezone, Boolean enabled) { }
    public record ImportResult(Long deviceId) { }
    public record RelayRequest(Boolean on) { }
}
