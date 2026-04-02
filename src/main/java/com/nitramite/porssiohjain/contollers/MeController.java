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
