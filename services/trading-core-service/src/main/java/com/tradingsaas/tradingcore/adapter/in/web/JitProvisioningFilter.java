package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.domain.model.Subscription;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JitProvisioningFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JitProvisioningFilter.class);

    private final UserRepository userRepository;

    public JitProvisioningFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String clerkUserId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        User user = resolveUser(clerkUserId, email, jwt);
        if (user == null) {
            chain.doFilter(request, response);
            return;
        }

        String plan = user.getSubscription() != null
                ? user.getSubscription().getPlan().name()
                : SubscriptionPlan.FREE.name();

        TokenClaims claims = new TokenClaims(user.getId(), user.getEmail(), plan);
        var newAuth = new UsernamePasswordAuthenticationToken(
                claims,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        newAuth.setDetails(jwtAuth.getDetails());
        SecurityContextHolder.getContext().setAuthentication(newAuth);

        chain.doFilter(request, response);
    }

    private User resolveUser(String clerkUserId, String email, Jwt jwt) {
        Optional<User> byClerk = userRepository.findByClerkUserId(clerkUserId);
        if (byClerk.isPresent()) {
            return byClerk.get();
        }

        if (email != null) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User migrated = byEmail.get().attachClerkUserId(clerkUserId);
                try {
                    User saved = userRepository.save(migrated);
                    log.info("Attached clerkUserId to migrated user userId={} email={}", saved.getId(), email);
                    return saved;
                } catch (Exception e) {
                    log.warn("Failed to attach clerkUserId to existing user email={}", email, e);
                    return byEmail.get();
                }
            }
        }

        return createFromClerk(clerkUserId, email, jwt);
    }

    private User createFromClerk(String clerkUserId, String email, Jwt jwt) {
        if (email == null) {
            log.warn("Clerk JWT sub={} has no email claim; cannot provision user", clerkUserId);
            return null;
        }
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        if (firstName == null) firstName = jwt.getClaimAsString("name");
        if (firstName == null) firstName = "";
        if (lastName == null) lastName = "";

        User newUser = User.fromClerk(clerkUserId, email, firstName, lastName);

        Subscription sub = new Subscription(
                UUID.randomUUID(),
                newUser.getId(),
                SubscriptionPlan.FREE,
                Instant.now(),
                null
        );
        newUser = new User(
                newUser.getId(),
                newUser.getClerkUserId(),
                newUser.getEmail(),
                newUser.getPasswordHash(),
                newUser.getFirstName(),
                newUser.getLastName(),
                newUser.getTimezone(),
                sub,
                newUser.getCreatedAt(),
                newUser.isActive()
        );

        try {
            User saved = userRepository.save(newUser);
            log.info("JIT-provisioned new user userId={} email={}", saved.getId(), email);
            return saved;
        } catch (Exception e) {
            log.error("Failed to JIT-provision user clerkUserId={} email={}", clerkUserId, email, e);
            return null;
        }
    }
}
