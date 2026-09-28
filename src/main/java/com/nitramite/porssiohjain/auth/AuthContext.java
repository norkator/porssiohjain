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
    private static final ThreadLocal<Boolean> adminAccountHolder = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> impersonatingHolder = new ThreadLocal<>();

    public void setAdminAccount(boolean admin) {
        adminAccountHolder.set(admin);
    }

    public boolean isAdminAccount() {
        return Boolean.TRUE.equals(adminAccountHolder.get());
    }

    public void setImpersonating(boolean impersonating) {
        impersonatingHolder.set(impersonating);
    }

    public boolean isImpersonating() {
        return Boolean.TRUE.equals(impersonatingHolder.get());
    }

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
        adminAccountHolder.remove();
        impersonatingHolder.remove();
    }
}
