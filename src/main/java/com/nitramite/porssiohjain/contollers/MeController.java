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
import com.nitramite.porssiohjain.services.AccountService;
import com.nitramite.porssiohjain.services.models.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@RequireAuth
public class MeController {

    private final AuthContext authContext;
    private final AccountService accountService;

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
                .createdAt(accountService.getCreatedAt(accountId))
                .build();
    }
}
