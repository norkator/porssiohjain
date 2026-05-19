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
import com.nitramite.porssiohjain.services.MarketNotificationService;
import com.nitramite.porssiohjain.services.models.MarketNotificationRequest;
import com.nitramite.porssiohjain.services.models.MarketNotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market-notifications")
@RequiredArgsConstructor
@RequireAuth
public class MarketNotificationsController {

    private final AuthContext authContext;
    private final MarketNotificationService marketNotificationService;

    @GetMapping
    public List<MarketNotificationResponse> getNotifications() {
        return marketNotificationService.getMarketNotifications(authContext.getAccountId());
    }

    @PostMapping
    public MarketNotificationResponse createNotification(@RequestBody MarketNotificationRequest request) {
        return marketNotificationService.createMarketNotification(authContext.getAccountId(), request);
    }

    @PutMapping("/{notificationId}")
    public MarketNotificationResponse updateNotification(
            @PathVariable Long notificationId,
            @RequestBody MarketNotificationRequest request
    ) {
        return marketNotificationService.updateMarketNotification(authContext.getAccountId(), notificationId, request);
    }

    @DeleteMapping("/{notificationId}")
    public void deleteNotification(@PathVariable Long notificationId) {
        marketNotificationService.deleteMarketNotification(authContext.getAccountId(), notificationId);
    }
}
