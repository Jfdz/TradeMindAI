package com.tradingsaas.tradingcore.adapter.out.security;

import com.tradingsaas.tradingcore.config.JwtProperties;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.port.out.JwtTokenPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
class JwtAdapter implements JwtTokenPort {

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;

    JwtAdapter(JwtProperties props) {
        this.secretKey = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = props.getAccessTokenExpiry() * 1000L;
    }

    @Override
    public String generateAccessToken(UUID userId, String email, String subscriptionPlan) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("plan", subscriptionPlan)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpiryMs))
                .signWith(secretKey)
                .compact();
    }

    @Override
    public TokenClaims validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new TokenClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("plan", String.class)
            );
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid or expired JWT", ex);
        }
    }
}
