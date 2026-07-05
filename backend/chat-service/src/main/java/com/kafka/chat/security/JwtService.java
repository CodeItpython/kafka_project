package com.kafka.chat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Validates the HS256 JWT minted by auth-service and reconstructs the caller from
 * its claims. chat-service shares the same {@code app.jwt.secret} as auth-service
 * but only ever *verifies* tokens — it never issues them. The signature is always
 * recomputed with HMAC-SHA256, so the alg header is irrelevant (no alg-confusion).
 */
@Service
@Slf4j
public class JwtService {
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final int MIN_SECRET_BYTES = 32;
    private static final String KNOWN_DEV_SECRET = "local-dev-secret-change-me-local-dev-secret";

    private final ObjectMapper objectMapper;
    private final String secret;

    public JwtService(ObjectMapper objectMapper, @Value("${app.jwt.secret}") String secret) {
        int secretLength = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretLength < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256. Set a strong JWT_SECRET environment variable.");
        }
        if (KNOWN_DEV_SECRET.equals(secret)) {
            log.warn("app.jwt.secret is the built-in development default. Set JWT_SECRET to a unique strong value before deploying.");
        }
        this.objectMapper = objectMapper;
        this.secret = secret;
    }

    /** Verifies the token and builds the caller from its claims. */
    public AuthUser parse(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        String expectedSignature = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }
        Map<?, ?> payload = decodePayload(parts[1]);
        Object exp = payload.get("exp");
        if (!(exp instanceof Number number) || number.longValue() < Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("Expired JWT");
        }
        Object subject = payload.get("sub");
        if (!(subject instanceof String email) || email.isBlank()) {
            throw new IllegalArgumentException("JWT subject is missing");
        }
        Long id = payload.get("uid") instanceof Number uid ? uid.longValue() : null;
        String name = payload.get("name") instanceof String value && !value.isBlank() ? value : email;
        String role = payload.get("role") instanceof String value && !value.isBlank() ? value : "USER";
        return new AuthUser(id, email, name, role);
    }

    /** Convenience for callers (e.g. STOMP CONNECT) that only need the email. */
    public String validateAndGetSubject(String token) {
        return parse(token).getEmail();
    }

    private Map<?, ?> decodePayload(String payload) {
        try {
            return objectMapper.readValue(BASE64_URL_DECODER.decode(payload), Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to decode JWT", e);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return BASE64_URL.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign JWT", e);
        }
    }
}
