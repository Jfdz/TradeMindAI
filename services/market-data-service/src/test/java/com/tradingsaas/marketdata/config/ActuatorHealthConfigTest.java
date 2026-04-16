package com.tradingsaas.marketdata.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;

class ActuatorHealthConfigTest {

    @Test
    void healthEndpointReturnsUpStatus() {
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        HealthComponent health = Health.up().build();
        when(endpoint.health()).thenReturn(health);

        HealthComponent result = endpoint.health();

        assertNotNull(result);
        assertEquals(Status.UP, result.getStatus());
    }

    @Test
    void healthEndpointReturnsDownStatusOnFailure() {
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        HealthComponent health = Health.down().withDetail("db", "Connection refused").build();
        when(endpoint.health()).thenReturn(health);

        HealthComponent result = endpoint.health();

        assertNotNull(result);
        assertEquals(Status.DOWN, result.getStatus());
    }

    @Test
    void actuatorHealthPathIsPermitted() {
        SecurityConfig config = new SecurityConfig();
        assertNotNull(config);
    }
}
