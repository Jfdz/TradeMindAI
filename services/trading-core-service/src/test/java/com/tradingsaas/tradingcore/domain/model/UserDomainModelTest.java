package com.tradingsaas.tradingcore.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserDomainModelTest {

    @Test
    void fromClerkCreatesActiveUserWithNullPasswordHash() {
        User user = User.fromClerk("user_abc", "alice@example.com", "Alice", "Smith");

        assertNotNull(user.getId());
        assertEquals("user_abc", user.getClerkUserId());
        assertEquals("alice@example.com", user.getEmail());
        assertNull(user.getPasswordHash());
        assertEquals("Alice", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals("UTC", user.getTimezone());
        assertTrue(user.isActive());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void fromClerkGeneratesUniqueIds() {
        User a = User.fromClerk("user_1", "a@example.com", "A", "B");
        User b = User.fromClerk("user_2", "b@example.com", "C", "D");

        assertTrue(!a.getId().equals(b.getId()));
    }

    @Test
    void attachClerkUserIdReturnsNewInstanceWithUpdatedField() {
        UUID id = UUID.randomUUID();
        User original = new User(id, null, "bob@example.com", "$2a$12$hash",
                "Bob", "Jones", "UTC", null, Instant.parse("2026-01-01T00:00:00Z"), true);

        User updated = original.attachClerkUserId("user_new_clerk");

        assertEquals("user_new_clerk", updated.getClerkUserId());
        assertEquals(id, updated.getId());
        assertEquals("bob@example.com", updated.getEmail());
        assertEquals("$2a$12$hash", updated.getPasswordHash());
        assertNull(original.getClerkUserId(), "original must not be mutated");
    }

    @Test
    void constructorRejectsBlankEmail() {
        assertThrows(IllegalArgumentException.class, () ->
                new User(UUID.randomUUID(), null, "  ", null, "F", "L", "UTC", null,
                        Instant.now(), true));
    }

    @Test
    void constructorDefaultsTimezoneToUtcWhenBlank() {
        User user = new User(UUID.randomUUID(), null, "c@example.com", null,
                "F", "L", "", null, Instant.now(), true);

        assertEquals("UTC", user.getTimezone());
    }
}
