package com.nitramite.porssiohjain.auth;

import org.springframework.stereotype.Component;

@Component
public class AuthContext {
    private static final ThreadLocal<Long> accountIdHolder = new ThreadLocal<>();

    public void setAccountId(Long accountId) {
        accountIdHolder.set(accountId);
    }

    public Long getAccountId() {
        return accountIdHolder.get();
    }

    public void clear() {
        accountIdHolder.remove();
    }
}
