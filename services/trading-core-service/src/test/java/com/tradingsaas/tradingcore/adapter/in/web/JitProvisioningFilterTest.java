package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JitProvisioningFilterTest {

    private UserRepository userRepository;
    private JitProvisioningFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        filter = new JitProvisioningFilter(userRepository);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsTokenClaimsPrincipalForKnownClerkUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User knownUser = user(userId, "user_abc123", "alice@example.com");
        when(userRepository.findByClerkUserId("user_abc123")).thenReturn(Optional.of(knownUser));

        SecurityContextHolder.getContext().setAuthentication(jwtAuth("user_abc123", "alice@example.com"));

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, auth);
        TokenClaims claims = (TokenClaims) auth.getPrincipal();
        assertEquals(userId, claims.userId());
        assertEquals("alice@example.com", claims.email());
        assertEquals("FREE", claims.subscriptionPlan());
        verify(chain).doFilter(request, response);
    }

    @Test
    void attachesClerkUserIdToMigratedUserFoundByEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        User existingUser = user(userId, null, "bob@example.com");
        User migrated = existingUser.attachClerkUserId("user_new999");
        when(userRepository.findByClerkUserId("user_new999")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenReturn(migrated);

        SecurityContextHolder.getContext().setAuthentication(jwtAuth("user_new999", "bob@example.com"));

        filter.doFilterInternal(request, response, chain);

        verify(userRepository).save(any(User.class));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        TokenClaims claims = (TokenClaims) auth.getPrincipal();
        assertEquals(userId, claims.userId());
        verify(chain).doFilter(request, response);
    }

    @Test
    void jitProvisionsBrandNewUserFromClerk() throws Exception {
        when(userRepository.findByClerkUserId("user_brand_new")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                jwtAuth("user_brand_new", "carol@example.com", "Carol", "Smith"));

        filter.doFilterInternal(request, response, chain);

        verify(userRepository).save(any(User.class));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, auth);
        TokenClaims claims = (TokenClaims) auth.getPrincipal();
        assertEquals("carol@example.com", claims.email());
        verify(chain).doFilter(request, response);
    }

    @Test
    void passesChainUnchangedWhenNoJwtAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(null);

        filter.doFilterInternal(request, response, chain);

        verify(userRepository, never()).findByClerkUserId(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void passesChainWhenJwtHasNoEmailAndUserNotFound() throws Exception {
        when(userRepository.findByClerkUserId("user_noemail")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(jwtAuth("user_noemail", null));

        filter.doFilterInternal(request, response, chain);

        verify(userRepository, never()).save(any());
        verify(chain).doFilter(request, response);
    }

    // --- helpers ---

    private static User user(UUID id, String clerkUserId, String email) {
        return new User(id, clerkUserId, email, null, "Test", "User", "UTC", null,
                Instant.parse("2026-01-01T00:00:00Z"), true);
    }

    private static JwtAuthenticationToken jwtAuth(String sub, String email) {
        return jwtAuth(sub, email, null, null);
    }

    private static JwtAuthenticationToken jwtAuth(String sub, String email,
                                                   String givenName, String familyName) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", sub);
        if (email != null) claims.put("email", email);
        if (givenName != null) claims.put("given_name", givenName);
        if (familyName != null) claims.put("family_name", familyName);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claims(c -> c.putAll(claims))
                .subject(sub)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        return new JwtAuthenticationToken(jwt, List.of());
    }
}
