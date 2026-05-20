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

package com.nitramite.porssiohjain.auth;

import org.springframework.stereotype.Component;

@Component
public class AuthContext {
    private static final ThreadLocal<Long> accountIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> demoAccountHolder = new ThreadLocal<>();

    public void setAccountId(Long accountId) {
        accountIdHolder.set(accountId);
    }

    public void setAccount(Long accountId, boolean demo) {
        accountIdHolder.set(accountId);
        demoAccountHolder.set(demo);
    }

    public Long getAccountId() {
        return accountIdHolder.get();
    }

    public boolean isDemoAccount() {
        return Boolean.TRUE.equals(demoAccountHolder.get());
    }

    public void clear() {
        accountIdHolder.remove();
        demoAccountHolder.remove();
    }
}
