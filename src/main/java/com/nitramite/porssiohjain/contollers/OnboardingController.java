/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
