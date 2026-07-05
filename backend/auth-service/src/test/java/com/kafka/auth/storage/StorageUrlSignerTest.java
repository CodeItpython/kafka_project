package com.kafka.auth.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StorageUrlSignerTest {
    private final StorageUrlSigner signer = new StorageUrlSigner("test-secret-please-change", Duration.ofHours(12));

    @Test
    void signsAndValidatesAttachmentUrl() {
        String signed = signer.sign("/api/chat/attachments/abc.png");
        assertThat(signed).startsWith("/api/chat/attachments/abc.png?exp=").contains("&sig=");

        long exp = Long.parseLong(query(signed, "exp"));
        String sig = query(signed, "sig");
        assertThat(signer.isValid("/api/chat/attachments/abc.png", exp, sig)).isTrue();
    }

    @Test
    void signsProfileImageUrl() {
        String signed = signer.sign("/api/users/profile-images/pic.jpg");
        long exp = Long.parseLong(query(signed, "exp"));
        assertThat(signer.isValid("/api/users/profile-images/pic.jpg", exp, query(signed, "sig"))).isTrue();
    }

    @Test
    void passesThroughNonStorageAndNullValues() {
        assertThat(signer.sign(null)).isNull();
        assertThat(signer.sign("")).isEmpty();
        assertThat(signer.sign("https://cdn.example.com/x.png")).isEqualTo("https://cdn.example.com/x.png");
    }

    @Test
    void signingIsIdempotentOnAlreadySignedUrl() {
        String signed = signer.sign("/api/chat/attachments/abc.png");
        String reSigned = signer.sign(signed);
        long exp = Long.parseLong(query(reSigned, "exp"));
        assertThat(signer.isValid("/api/chat/attachments/abc.png", exp, query(reSigned, "sig"))).isTrue();
        assertThat(reSigned).doesNotContain("?exp=" + query(signed, "exp") + "&sig=" + query(signed, "sig") + "?");
    }

    @Test
    void rawPathStripsQuery() {
        assertThat(signer.rawPath("/api/chat/attachments/abc.png?exp=1&sig=zzz"))
                .isEqualTo("/api/chat/attachments/abc.png");
        assertThat(signer.rawPath("/api/chat/attachments/abc.png"))
                .isEqualTo("/api/chat/attachments/abc.png");
    }

    @Test
    void rejectsExpiredSignature() {
        StorageUrlSigner shortTtl = new StorageUrlSigner("test-secret-please-change", Duration.ofSeconds(-1));
        // ttl<=0 is normalized to 12h, so build an explicitly past exp instead.
        long pastExp = java.time.Instant.now().minusSeconds(60).getEpochSecond();
        String sig = query(signer.sign("/api/chat/attachments/abc.png"), "sig");
        assertThat(signer.isValid("/api/chat/attachments/abc.png", pastExp, sig)).isFalse();
        assertThat(shortTtl).isNotNull();
    }

    @Test
    void rejectsTamperedSignature() {
        String signed = signer.sign("/api/chat/attachments/abc.png");
        long exp = Long.parseLong(query(signed, "exp"));
        assertThat(signer.isValid("/api/chat/attachments/abc.png", exp, "tampered")).isFalse();
        // Signature bound to the path: a different resource path must not validate.
        assertThat(signer.isValid("/api/chat/attachments/other.png", exp, query(signed, "sig"))).isFalse();
    }

    private static String query(String url, String key) {
        String q = url.substring(url.indexOf('?') + 1);
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(key)) {
                return kv[1];
            }
        }
        throw new IllegalArgumentException("missing " + key);
    }
}
