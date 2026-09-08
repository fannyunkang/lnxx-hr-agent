package com.lnxx.hr.security;

import java.security.Principal;

public record AuthPrincipal(String username, String employeeId, UserRole role) implements Principal {
    @Override
    public String getName() {
        return username;
    }
}

