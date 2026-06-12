package com.kafka.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Setter
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private AuthProvider provider = AuthProvider.LOCAL;

    @Setter
    private String providerId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UserAccount(String email, String name, String passwordHash, AuthProvider provider) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.provider = provider;
    }

    public void setName(String name) {
        this.name = name;
    }
}
