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
import com.nitramite.porssiohjain.services.QrLoginService;
import com.nitramite.porssiohjain.services.models.CreateQrLoginChallengeRequest;
import com.nitramite.porssiohjain.services.models.QrLoginApproveRequest;
import com.nitramite.porssiohjain.services.models.QrLoginCompleteRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/account/qr-login")
@RequiredArgsConstructor
public class QrLoginController {

    private final QrLoginService qrLoginService;
    private final AuthContext authContext;
    private final HttpServletRequest request;

    @PostMapping("/challenges")
    public ResponseEntity<?> createChallenge(@RequestBody(required = false) CreateQrLoginChallengeRequest requestBody) {
        String apiBaseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();

        return ResponseEntity.ok(qrLoginService.createChallenge(getClientIp(), apiBaseUrl, requestBody));
    }

    @PostMapping("/challenges/{challengeId}/approve")
    @RequireAuth
    public ResponseEntity<?> approveChallenge(
            @PathVariable UUID challengeId,
            @RequestBody QrLoginApproveRequest requestBody
    ) {
        return ResponseEntity.ok(qrLoginService.approveChallenge(
                challengeId,
                requestBody.getScanSecret(),
                authContext.getAccountId()
        ));
    }

    @PostMapping("/challenges/{challengeId}/complete")
    public ResponseEntity<?> completeChallenge(
            @PathVariable UUID challengeId,
            @RequestBody QrLoginCompleteRequest requestBody
    ) {
        return ResponseEntity.ok(qrLoginService.completeChallenge(challengeId, requestBody.getBrowserSecret()));
    }

    @PostMapping("/challenges/{challengeId}/cancel")
    public ResponseEntity<?> cancelChallenge(
            @PathVariable UUID challengeId,
            @RequestBody QrLoginCompleteRequest requestBody
    ) {
        return ResponseEntity.ok(qrLoginService.cancelChallenge(challengeId, requestBody.getBrowserSecret()));
    }

    private String getClientIp() {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0] : request.getRemoteAddr();
    }
}
