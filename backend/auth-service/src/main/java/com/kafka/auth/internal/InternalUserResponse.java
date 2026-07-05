package com.kafka.auth.internal;

import com.kafka.auth.model.UserAccount;

/**
 * User projection returned to trusted internal callers (chat-service). The
 * profileImageUrl is the raw stored path — the caller signs it with the shared
 * storage-signing secret when it needs a downloadable URL.
 */
public record InternalUserResponse(
        Long id,
        String email,
        String name,
        String provider,
        String statusMessage,
        String profileImageUrl
) {
    public static InternalUserResponse from(UserAccount user) {
        return new InternalUserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProvider().name(),
                user.getStatusMessage(),
                user.getProfileImageUrl()
        );
    }
}
