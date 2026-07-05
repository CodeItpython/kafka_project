package com.kafka.auth.security;

import com.kafka.auth.model.UserAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class JwtService {
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    /** HS256 needs a key of at least 256 bits (32 bytes) to be secure. */
    private static final int MIN_SECRET_BYTES = 32;
    private static final String KNOWN_DEV_SECRET = "local-dev-secret-change-me-local-dev-secret";

    private final ObjectMapper objectMapper;
    private final String secret;
    private final String issuer;
    private final long expirationMinutes;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes
    ) {
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
        this.issuer = issuer;
        this.expirationMinutes = expirationMinutes;
    }

    public String createToken(UserAccount user) {
        Instant now = Instant.now();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", user.getEmail());
        payload.put("uid", user.getId());
        payload.put("name", user.getName());
        payload.put("role", user.getRole().name());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plusSeconds(expirationMinutes * 60).getEpochSecond());

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signature = sign(encodedHeader + "." + encodedPayload);
        return encodedHeader + "." + encodedPayload + "." + signature;
    }

    public String validateAndGetSubject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        String expectedSignature = sign(parts[0] + "." + parts[1]);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
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
        return email;
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to encode JWT", e);
        }
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

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigestHelper.equals(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
