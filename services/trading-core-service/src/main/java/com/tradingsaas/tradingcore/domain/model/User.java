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
    private final String clerkUserId;
    private final String email;
    private final String passwordHash;
    private final String firstName;
    private final String lastName;
    private final String timezone;
    private final Subscription subscription;
    private final Instant createdAt;
    private final boolean active;

    public User(UUID id,
                String clerkUserId,
                String email,
                String passwordHash,
                String firstName,
                String lastName,
                String timezone,
                Subscription subscription,
                Instant createdAt,
                boolean active) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        this.id = id;
        this.clerkUserId = clerkUserId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.timezone = timezone == null || timezone.isBlank() ? "UTC" : timezone;
        this.subscription = subscription;
        this.createdAt = createdAt;
        this.active = active;
    }

    /** Factory for users provisioned via Clerk (no local password). */
    public static User fromClerk(String clerkUserId,
                                 String email,
                                 String firstName,
                                 String lastName) {
        return new User(
                UUID.randomUUID(),
                clerkUserId,
                email,
                null,
                firstName,
                lastName,
                "UTC",
                null,
                Instant.now(),
                true
        );
    }

    /** Returns a new User with clerkUserId attached (for migrated password users). */
    public User attachClerkUserId(String newClerkUserId) {
        return new User(this.id, newClerkUserId, this.email, this.passwordHash,
                this.firstName, this.lastName, this.timezone,
                this.subscription, this.createdAt, this.active);
    }

    public UUID getId() {
        return id;
    }

    public String getClerkUserId() {
        return clerkUserId;
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

    public String getTimezone() {
        return timezone;
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
