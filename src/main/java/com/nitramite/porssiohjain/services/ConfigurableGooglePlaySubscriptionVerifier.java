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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.services.models.VerifiedGooglePlaySubscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class ConfigurableGooglePlaySubscriptionVerifier implements GooglePlaySubscriptionVerifier {

    private final String verificationMode;

    public ConfigurableGooglePlaySubscriptionVerifier(
            @Value("${app.billing.google-play.verification-mode:disabled}") String verificationMode
    ) {
        this.verificationMode = verificationMode;
    }

    @Override
    public VerifiedGooglePlaySubscription verifySubscription(String productId, String purchaseToken) {
        if ("mock".equalsIgnoreCase(verificationMode)) {
            return VerifiedGooglePlaySubscription.builder()
                    .productId(productId)
                    .purchaseToken(purchaseToken)
                    .active(true)
                    .autoRenewing(true)
                    .acknowledged(true)
                    .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                    .build();
        }

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Google Play subscription verification is not configured."
        );
    }
}
