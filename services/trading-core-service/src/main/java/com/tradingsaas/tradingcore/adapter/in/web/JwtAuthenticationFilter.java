package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.port.out.JwtTokenPort;
import com.tradingsaas.tradingcore.domain.port.out.TokenBlacklistPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenPort jwtTokenPort;
    private final TokenBlacklistPort tokenBlacklistPort;
    private final Set<String> adminEmails;

    JwtAuthenticationFilter(
            JwtTokenPort jwtTokenPort,
            TokenBlacklistPort tokenBlacklistPort,
            @Value("${trading-core.admin-emails:}") String adminEmailsCsv) {
        this.jwtTokenPort = jwtTokenPort;
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.adminEmails = Arrays.stream(adminEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractBearer(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            if (tokenBlacklistPort.isBlacklisted(token)) {
                throw new BadCredentialsException("Token has been revoked");
            }
            TokenClaims claims = jwtTokenPort.validateAccessToken(token);
            List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            if (claims.email() != null && adminEmails.contains(claims.email().toLowerCase())) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            var auth = new UsernamePasswordAuthenticationToken(claims, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (BadCredentialsException ignored) {
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
