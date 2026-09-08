package com.lnxx.hr.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class TokenService {
    private final byte[] secret;
    private final long validitySeconds;

    public TokenService(@Value("${hr-agent.security.token-secret}") String secret,
                        @Value("${hr-agent.security.token-validity-seconds}") long validitySeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.validitySeconds = validitySeconds;
    }

    public String issue(DemoUser user) {
        String payload = String.join("|", user.username(), user.employeeId(), user.role().name(),
                Long.toString(Instant.now().plusSeconds(validitySeconds).getEpochSecond()));
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    public Optional<AuthPrincipal> verify(String token) {
        try {
            String[] parts = token.split("\\.", 2);
            if (parts.length != 2 || !constantTimeEquals(sign(parts[0]), parts[1])) {
                return Optional.empty();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", 4);
            if (fields.length != 4 || Instant.now().getEpochSecond() >= Long.parseLong(fields[3])) {
                return Optional.empty();
            }
            return Optional.of(new AuthPrincipal(fields[0], fields[1], UserRole.valueOf(fields[2])));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建访问令牌", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}

