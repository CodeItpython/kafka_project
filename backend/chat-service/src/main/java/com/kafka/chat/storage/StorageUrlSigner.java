package com.kafka.chat.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signs and validates time-limited access URLs for stored objects
 * (chat attachments, profile images). These are served to the browser via
 * {@code <img src>} which cannot send an Authorization header, so instead of
 * leaving the endpoints fully public we append an HMAC signature and an expiry:
 * {@code /api/.../{name}?exp={epochSeconds}&sig={base64url-hmac}}.
 *
 * Storage always keeps the raw path (see {@link #rawPath}); signing happens only
 * when a URL is emitted in a response, so each read gets a fresh expiry.
 * Signing is idempotent: an already-signed URL is stripped to its raw path
 * before re-signing.
 */
@Component
public class StorageUrlSigner {
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final String[] SIGNABLE_PREFIXES = {
            "/api/chat/attachments/",
            "/api/users/profile-images/"
    };

    private final byte[] secret;
    private final Duration ttl;

    public StorageUrlSigner(
            @Value("${app.storage.signing.secret:local-dev-storage-signing-secret-change-me}") String secret,
            @Value("${app.storage.signing.ttl:12h}") Duration ttl
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofHours(12) : ttl;
    }

    /** Returns a signed, expiring URL for a stored-object path; passes other values through unchanged. */
    public String sign(String url) {
        String path = rawPath(url);
        if (path == null || !isSignable(path)) {
            return url;
        }
        long exp = Instant.now().plus(ttl).getEpochSecond();
        return path + "?exp=" + exp + "&sig=" + signature(path, exp);
    }

    /** Strips any query string so the canonical stored value is a bare path. */
    public String rawPath(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }

    /** Validates the {@code exp}/{@code sig} query params for a served resource path. */
    public boolean isValid(String resourcePath, Long exp, String sig) {
        if (resourcePath == null || exp == null || sig == null || sig.isBlank()) {
            return false;
        }
        if (Instant.now().getEpochSecond() > exp) {
            return false;
        }
        String expected = signature(resourcePath, exp);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isSignable(String path) {
        for (String prefix : SIGNABLE_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String signature(String path, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return BASE64_URL.encodeToString(mac.doFinal((path + "|" + exp).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("스토리지 URL 서명에 실패했습니다.", exception);
        }
    }
}
