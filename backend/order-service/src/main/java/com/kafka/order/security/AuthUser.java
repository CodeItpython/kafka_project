package com.kafka.order.security;

/**
 * The authenticated principal in order-service, reconstructed purely from the
 * signed JWT claims (uid/sub/name/role) — no database lookup. Orders are scoped by
 * {@link #getEmail()} (the JWT subject), matching how shopping-service scopes carts.
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
