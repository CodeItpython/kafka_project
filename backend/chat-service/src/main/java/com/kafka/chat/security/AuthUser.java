package com.kafka.chat.security;

/**
 * The authenticated principal in chat-service. Unlike auth-service, chat-service
 * does not own the user table — it reconstructs the caller's identity purely from
 * the signed JWT claims (uid/sub/name/role), so no database lookup is needed to
 * authenticate a request. Details about *other* users are fetched over REST via
 * {@code UserDirectoryClient}.
 */
public class AuthUser {
    private final Long id;
    private final String email;
    private final String name;
    private final String role;

    public AuthUser(Long id, String email, String name, String role) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String toString() {
        return email;
    }
}
