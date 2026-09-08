package com.lnxx.hr.internalapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AgentServiceTokenVerifier {
    private final byte[] expected;

    public AgentServiceTokenVerifier(@Value("${hr-agent.agent.service-token}") String expected) {
        this.expected = expected.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String provided) {
        byte[] actual = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Agent service token is invalid");
        }
    }
}
