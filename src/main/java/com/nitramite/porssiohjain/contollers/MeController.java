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
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.AccountService;
import com.nitramite.porssiohjain.services.PushNotificationTokenService;
import com.nitramite.porssiohjain.services.models.ChangePasswordRequest;
import com.nitramite.porssiohjain.services.models.MeResponse;
import com.nitramite.porssiohjain.services.models.PushNotificationTokenRequest;
import com.nitramite.porssiohjain.services.models.PushNotificationTokenResponse;
import com.nitramite.porssiohjain.services.models.UpdateMeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@RequireAuth
public class MeController {

    private final AuthContext authContext;
    private final AccountService accountService;
    private final AccountLimitService accountLimitService;
    private final PushNotificationTokenService pushNotificationTokenService;

    @GetMapping
    public MeResponse getMe() {
        Long accountId = authContext.getAccountId();
        return MeResponse.builder()
                .accountId(accountId)
                .uuid(accountService.getUuidById(accountId))
                .tier(accountService.getTier(accountId))
                .email(accountService.getEmail(accountId))
                .locale(accountService.getLocale(accountId))
                .notifyPowerLimitExceeded(accountService.getNotifyPowerLimitExceeded(accountId))
                .notifyControlActivated(accountService.getNotifyControlActivated(accountId))
                .emailNotificationsEnabled(accountService.getEmailNotificationsEnabled(accountId))
                .pushNotificationsEnabled(accountService.getPushNotificationsEnabled(accountId))
                .createdAt(accountService.getCreatedAt(accountId))
                .deviceLimit(accountLimitService.getEffectiveDeviceLimit(accountId))
                .controlLimit(accountLimitService.getEffectiveControlLimit(accountId))
                .productionSourceLimit(accountLimitService.getEffectiveProductionSourceLimit(accountId))
                .weatherControlLimit(accountLimitService.getEffectiveWeatherControlLimit(accountId))
                .weeklyEmailNotificationLimit(accountLimitService.getEffectiveWeeklyEmailNotificationLimit(accountId))
                .weeklyPushNotificationLimit(accountLimitService.getEffectiveWeeklyPushNotificationLimit(accountId))
                .build();
    }

    @PutMapping
    public MeResponse updateMe(@RequestBody UpdateMeRequest request) {
        Long accountId = authContext.getAccountId();
        accountService.updateAccountSettings(
                accountId,
                request.getEmail(),
                request.getNotifyPowerLimitExceeded() != null
                        ? request.getNotifyPowerLimitExceeded()
                        : accountService.getNotifyPowerLimitExceeded(accountId),
                request.getNotifyControlActivated() != null
                        ? request.getNotifyControlActivated()
                        : accountService.getNotifyControlActivated(accountId),
                request.getEmailNotificationsEnabled() != null
                        ? request.getEmailNotificationsEnabled()
                        : accountService.getEmailNotificationsEnabled(accountId),
                request.getPushNotificationsEnabled() != null
                        ? request.getPushNotificationsEnabled()
                        : accountService.getPushNotificationsEnabled(accountId),
                request.getLocale()
        );
        return getMe();
    }

    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        Long accountId = authContext.getAccountId();

        try {
            boolean changed = accountService.changeSecret(
                    accountId,
                    request.getCurrentPassword(),
                    request.getNewPassword()
            );

            if (!changed) {
                return ResponseEntity.badRequest().body("Current password is incorrect");
            }

            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @GetMapping("/push-tokens")
    public java.util.List<PushNotificationTokenResponse> getPushTokens() {
        return pushNotificationTokenService.getPushNotificationTokens(authContext.getAccountId());
    }

    @PostMapping("/push-tokens")
    public PushNotificationTokenResponse registerPushToken(@RequestBody PushNotificationTokenRequest request) {
        return pushNotificationTokenService.registerPushNotificationToken(
                authContext.getAccountId(),
                request.getToken(),
                request.getPlatform(),
                request.getDeviceName()
        );
    }

    @DeleteMapping("/push-tokens/{tokenId}")
    public ResponseEntity<Void> deletePushToken(@PathVariable Long tokenId) {
        pushNotificationTokenService.deletePushNotificationToken(authContext.getAccountId(), tokenId);
        return ResponseEntity.noContent().build();
    }
}
