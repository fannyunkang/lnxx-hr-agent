package com.lnxx.hr.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {
    @Test void issuesAndVerifiesSignedToken() {
        TokenService service = new TokenService("a-long-enough-test-secret", 60);
        String token = service.issue(new DemoUser("employee", "secret", "E1001", "张伟", UserRole.EMPLOYEE));
        assertThat(service.verify(token)).get().extracting(AuthPrincipal::employeeId).isEqualTo("E1001");
        assertThat(service.verify(token + "tampered")).isEmpty();
    }
}

