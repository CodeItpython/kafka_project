package com.kafka.chat.client;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Facade over {@link AuthInternalUserClient} that replaces the direct
 * UserAccountRepository access chat-service used to have. This is the single
 * seam through which chat-service reads user data owned by auth-service.
 */
@Component
@RequiredArgsConstructor
public class UserDirectoryClient {
    private final AuthInternalUserClient client;

    public Optional<UserView> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return client.byEmails(List.of(email)).stream()
                .filter(user -> user.getEmail() != null && user.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    public List<UserView> findByEmails(Collection<String> emails) {
        List<String> normalized = emails.stream()
                .filter(email -> email != null && !email.isBlank())
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return client.byEmails(normalized);
    }

    /** Directory search for the contacts list. A blank query returns the first users by name. */
    public List<UserView> search(String query, int limit) {
        return client.search(query == null || query.isBlank() ? null : query.trim(), limit);
    }

    /** All users — used only by legacy "everyone" group rooms that have no explicit participant list. */
    public List<UserView> findAll() {
        return client.all();
    }
}
