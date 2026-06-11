package com.kafka.auth.security;

import java.security.MessageDigest;

final class MessageDigestHelper {
    private MessageDigestHelper() {
    }

    static boolean equals(byte[] left, byte[] right) {
        return MessageDigest.isEqual(left, right);
    }
}
