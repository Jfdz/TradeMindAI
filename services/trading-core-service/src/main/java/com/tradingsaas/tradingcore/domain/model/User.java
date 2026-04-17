package com.tradingsaas.tradingcore.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Core user domain entity.
 * No JPA or Spring annotations — pure domain model.
 */
public class User {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String firstName;
    private final String lastName;
    private final Subscription subscription;
    private final Instant createdAt;
    private final boolean active;

    public User(UUID id,
                String email,
                String passwordHash,
                String firstName,
                String lastName,
                Subscription subscription,
                Instant createdAt,
                boolean active) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.subscription = subscription;
        this.createdAt = createdAt;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", email='" + email + '\'' + ", active=" + active + '}';
    }
}
