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
import com.nitramite.porssiohjain.services.PowerLimitNotificationService;
import com.nitramite.porssiohjain.services.PowerLimitService;
import com.nitramite.porssiohjain.services.models.PowerLimitDeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitHistoryResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitNotificationResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/power-limits")
@RequiredArgsConstructor
@RequireAuth
public class PowerLimitsController {

    private final AuthContext authContext;
    private final PowerLimitService powerLimitService;
    private final PowerLimitNotificationService powerLimitNotificationService;

    @GetMapping
    public List<PowerLimitResponse> listLimits() {
        return powerLimitService.getAllLimits(authContext.getAccountId());
    }

    @PostMapping
    public PowerLimitResponse createLimit(@RequestBody PowerLimitRequest request) {
        return powerLimitService.createLimit(
                authContext.getAccountId(),
                request.name(),
                request.limitKw().doubleValue(),
                request.enabled()
        );
    }

    @GetMapping("/{powerLimitId}")
    public PowerLimitResponse getLimit(@PathVariable Long powerLimitId) {
        return powerLimitService.getPowerLimit(authContext.getAccountId(), powerLimitId);
    }

    @GetMapping("/{powerLimitId}/history")
    public List<PowerLimitHistoryResponse> getHistory(
            @PathVariable Long powerLimitId,
            @RequestParam(defaultValue = "24") int hours
    ) {
        return powerLimitService.getPowerLimitHistoryWithInterval(authContext.getAccountId(), powerLimitId, hours);
    }

    @PutMapping("/{powerLimitId}")
    public PowerLimitResponse updateLimit(
            @PathVariable Long powerLimitId,
            @RequestBody PowerLimitRequest request
    ) {
        Long accountId = authContext.getAccountId();
        powerLimitService.updatePowerLimit(
                accountId,
                powerLimitId,
                request.name(),
                request.limitKw(),
                request.limitIntervalMinutes(),
                request.enabled(),
                request.notifyEnabled(),
                request.timezone(),
                request.siteId(),
                null,
                null
        );
        return powerLimitService.getPowerLimit(accountId, powerLimitId);
    }

    @DeleteMapping("/{powerLimitId}")
    public void deleteLimit(@PathVariable Long powerLimitId) {
        powerLimitService.deletePowerLimit(authContext.getAccountId(), powerLimitId);
    }

    @GetMapping("/{powerLimitId}/devices")
    public List<PowerLimitDeviceResponse> getDevices(@PathVariable Long powerLimitId) {
        return powerLimitService.getPowerLimitDevices(authContext.getAccountId(), powerLimitId);
    }

    @PostMapping("/{powerLimitId}/devices")
    public void addDevice(@PathVariable Long powerLimitId, @RequestBody PowerLimitDeviceRequest request) {
        powerLimitService.addDeviceToPowerLimit(
                authContext.getAccountId(),
                powerLimitId,
                request.deviceId(),
                request.deviceChannel()
        );
    }

    @DeleteMapping("/devices/{linkId}")
    public void deleteDevice(@PathVariable Long linkId) {
        powerLimitService.deletePowerLimitDevice(authContext.getAccountId(), linkId);
    }

    @GetMapping("/{powerLimitId}/notifications")
    public List<PowerLimitNotificationResponse> getNotifications(@PathVariable Long powerLimitId) {
        return powerLimitNotificationService.getPowerLimitNotifications(authContext.getAccountId(), powerLimitId);
    }

    @PostMapping("/{powerLimitId}/notifications")
    public PowerLimitNotificationResponse addNotification(
            @PathVariable Long powerLimitId,
            @RequestBody PowerLimitNotificationRequest request
    ) {
        return powerLimitNotificationService.createPowerLimitNotification(
                authContext.getAccountId(),
                powerLimitId,
                request.name(),
                request.description(),
                request.activeFrom(),
                request.activeTo(),
                request.enabled(),
                request.triggerKw()
        );
    }

    @PutMapping("/notifications/{notificationId}")
    public PowerLimitNotificationResponse updateNotification(
            @PathVariable Long notificationId,
            @RequestBody PowerLimitNotificationUpdateRequest request
    ) {
        return powerLimitNotificationService.updatePowerLimitNotification(
                authContext.getAccountId(),
                request.powerLimitId(),
                notificationId,
                request.name(),
                request.description(),
                request.activeFrom(),
                request.activeTo(),
                request.enabled(),
                request.triggerKw()
        );
    }

    @DeleteMapping("/{powerLimitId}/notifications/{notificationId}")
    public void deleteNotification(@PathVariable Long powerLimitId, @PathVariable Long notificationId) {
        powerLimitNotificationService.deletePowerLimitNotification(authContext.getAccountId(), powerLimitId, notificationId);
    }

    public record PowerLimitRequest(
            String name,
            BigDecimal limitKw,
            boolean enabled,
            boolean notifyEnabled,
            String timezone,
            Integer limitIntervalMinutes,
            Long siteId
    ) {
    }

    public record PowerLimitDeviceRequest(Long deviceId, int deviceChannel) {
    }

    public record PowerLimitNotificationRequest(
            String name,
            String description,
            java.time.LocalTime activeFrom,
            java.time.LocalTime activeTo,
            boolean enabled,
            Double triggerKw
    ) {
    }

    public record PowerLimitNotificationUpdateRequest(
            Long powerLimitId,
            String name,
            String description,
            java.time.LocalTime activeFrom,
            java.time.LocalTime activeTo,
            boolean enabled,
            Double triggerKw
    ) {
    }
}
