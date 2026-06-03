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
import com.nitramite.porssiohjain.services.BillingService;
import com.nitramite.porssiohjain.services.models.BillingProductResponse;
import com.nitramite.porssiohjain.services.models.BillingStatusResponse;
import com.nitramite.porssiohjain.services.models.GooglePlayPurchaseRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
@RequireAuth
public class BillingController {

    private final AuthContext authContext;
    private final BillingService billingService;

    @GetMapping("/products")
    public List<BillingProductResponse> getProducts() {
        return billingService.getProducts();
    }

    @GetMapping("/status")
    public BillingStatusResponse getStatus() {
        return billingService.getBillingStatus(authContext.getAccountId());
    }

    @PostMapping("/google-play/purchases")
    public BillingStatusResponse handleGooglePlayPurchase(@RequestBody GooglePlayPurchaseRequest request) {
        return billingService.handleGooglePlayPurchase(
                authContext.getAccountId(),
                request.getProductId(),
                request.getPurchaseToken()
        );
    }
}
