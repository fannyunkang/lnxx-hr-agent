package com.lnxx.hr.agentgateway;

import com.lnxx.hr.security.AuthPrincipal;

public final class TrustedUserContext {
    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();
    private TrustedUserContext() {}
    public static void set(AuthPrincipal principal) { CURRENT.set(principal); }
    public static AuthPrincipal require() {
        AuthPrincipal principal = CURRENT.get();
        if (principal == null) throw new SecurityException("TRUSTED_USER_CONTEXT_MISSING");
        return principal;
    }
    public static void clear() { CURRENT.remove(); }
}
