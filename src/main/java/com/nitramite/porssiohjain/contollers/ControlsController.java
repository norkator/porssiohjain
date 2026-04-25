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
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.services.ControlChartService;
import com.nitramite.porssiohjain.services.ControlNotificationService;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/controls")
@RequiredArgsConstructor
@RequireAuth
public class ControlsController {

    private final AuthContext authContext;
    private final ControlService controlService;
    private final ControlChartService controlChartService;
    private final ControlNotificationService controlNotificationService;

    @GetMapping
    public List<ControlResponse> listControls() {
        return controlService.getAllControls(authContext.getAccountId());
    }

    @PostMapping
    public ControlEntity createControl(
            @RequestBody CreateControlRequest request
    ) {
        Long accountId = authContext.getAccountId();
        return controlService.createControl(
                accountId,
                request.getName(),
                request.getTimezone(),
                request.getMaxPriceSnt(),
                request.getMinPriceSnt(),
                request.getDailyOnMinutes(),
                request.getTaxPercent(),
                request.getMode(),
                request.getManualOn(),
                request.getAlwaysOnBelowMinPrice()
        );
    }

    @GetMapping("/{controlId}")
    public ControlResponse getControl(
            @PathVariable Long controlId
    ) {
        return controlService.getControl(authContext.getAccountId(), controlId);
    }

    @PutMapping("/{controlId}")
    public ControlResponse updateControl(
            @PathVariable Long controlId,
            @RequestBody UpdateControlRequest request
    ) {
        Long accountId = authContext.getAccountId();
        controlService.updateControl(
                accountId,
                controlId,
                request.getName(),
                request.getMaxPriceSnt(),
                request.getMinPriceSnt(),
                request.getDailyOnMinutes(),
                request.getTaxPercent(),
                request.getMode(),
                request.getManualOn(),
                request.getAlwaysOnBelowMinPrice(),
                request.getEnergyContractId(),
                request.getTransferContractId(),
                request.getSiteId()
        );
        return controlService.getControl(accountId, controlId);
    }

    @GetMapping("/{controlId}/chart")
    public ControlChartResponse getControlChart(
            @PathVariable Long controlId
    ) {
        return controlChartService.getControlChart(authContext.getAccountId(), controlId);
    }

    @DeleteMapping("/{controlId}")
    public void deleteControl(
            @PathVariable Long controlId
    ) {
        controlService.deleteControl(authContext.getAccountId(), controlId);
    }

    @GetMapping("/{controlId}/links")
    public List<ControlDeviceResponse> getControlLinks(
            @PathVariable Long controlId
    ) {
        return controlService.getControlDeviceLinks(authContext.getAccountId(), controlId);
    }

    @PostMapping("/{controlId}/links/devices")
    public ControlDeviceResponse addDeviceLink(
            @PathVariable Long controlId,
            @RequestBody ControlDeviceLinkRequest request
    ) {
        return controlService.addDeviceToControl(
                authContext.getAccountId(),
                controlId,
                request.getDeviceId(),
                request.getDeviceChannel(),
                request.getEstimatedPowerKw()
        );
    }

    @GetMapping("/{controlId}/notifications")
    public List<ControlNotificationResponse> getControlNotifications(
            @PathVariable Long controlId
    ) {
        return controlNotificationService.getControlNotifications(authContext.getAccountId(), controlId);
    }

    @PostMapping("/{controlId}/notifications")
    public ControlNotificationResponse createControlNotification(
            @PathVariable Long controlId,
            @RequestBody ControlNotificationRequest request
    ) {
        return controlNotificationService.createControlNotification(
                authContext.getAccountId(),
                controlId,
                request.getName(),
                request.getDescription(),
                request.getActiveFrom(),
                request.getActiveTo(),
                request.isEnabled(),
                request.getCheapestHours(),
                request.getSendEarlierMinutes()
        );
    }

    @PutMapping("/{controlId}/notifications/{notificationId}")
    public ControlNotificationResponse updateControlNotification(
            @PathVariable Long controlId,
            @PathVariable Long notificationId,
            @RequestBody ControlNotificationRequest request
    ) {
        controlService.getControl(authContext.getAccountId(), controlId);
        return controlNotificationService.updateControlNotification(
                authContext.getAccountId(),
                controlId,
                notificationId,
                request.getName(),
                request.getDescription(),
                request.getActiveFrom(),
                request.getActiveTo(),
                request.isEnabled(),
                request.getCheapestHours(),
                request.getSendEarlierMinutes()
        );
    }

    @DeleteMapping("/{controlId}/notifications/{notificationId}")
    public void deleteControlNotification(
            @PathVariable Long controlId,
            @PathVariable Long notificationId
    ) {
        controlNotificationService.deleteControlNotification(authContext.getAccountId(), controlId, notificationId);
    }
}
