package com.lnxx.hr.security;

public record DemoUser(String username, String password, String employeeId, String displayName, UserRole role) {
}

