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
import com.nitramite.porssiohjain.services.OnboardingService;
import com.nitramite.porssiohjain.services.models.OnboardingChecklistResponse;
import com.nitramite.porssiohjain.services.models.OnboardingStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
@RequireAuth
public class OnboardingController {

    private final AuthContext authContext;
    private final OnboardingService onboardingService;

    @GetMapping("/status")
    public OnboardingStatusResponse getStatus() {
        return onboardingService.getStatus(authContext.getAccountId());
    }

    @GetMapping("/checklist")
    public OnboardingChecklistResponse getChecklist() {
        return onboardingService.getChecklist(authContext.getAccountId());
    }
}
