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

import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemoAccountGuard {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public void assertWritable(Long accountId) {
        if (accountId == null) {
            return;
        }

        boolean demo = accountRepository.findById(accountId)
                .map(account -> account.isDemo())
                .orElse(false);

        if (demo) {
            throw new DemoAccountMutationException();
        }
    }
}
