package com.tradingsaas.tradingcore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalSecretFilterTest {

    @Test
    void allowsRequestToInternalPathWithMatchingSecret() throws Exception {
        InternalSecretFilter filter = new InternalSecretFilter("super-secret");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/internal/reasoning-context/AAPL");
        req.addHeader("X-Internal-Secret", "super-secret");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        verify(chain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void rejectsInternalPathWithoutHeader() throws Exception {
        InternalSecretFilter filter = new InternalSecretFilter("super-secret");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/internal/reasoning-context/AAPL");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsInternalPathWithWrongSecret() throws Exception {
        InternalSecretFilter filter = new InternalSecretFilter("super-secret");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/internal/reasoning-context/AAPL");
        req.addHeader("X-Internal-Secret", "WRONG");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void returns503WhenSecretNotConfigured() throws Exception {
        InternalSecretFilter filter = new InternalSecretFilter("");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/internal/reasoning-context/AAPL");
        req.addHeader("X-Internal-Secret", "anything");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(503, res.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void passesThroughNonInternalPathsWithoutCheckingSecret() throws Exception {
        InternalSecretFilter filter = new InternalSecretFilter("super-secret");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/signals/123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        verify(chain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    // VERIFICATION B — categorise 401 responses from the trading-core filter
    // so the prod log fingerprint ("Missing or invalid X-Internal-Secret" +
    // absence of WWW-Authenticate) maps unambiguously to InternalSecretFilter,
    // not Spring Security. This rules out suspect #1 in the user's triage.
    @Test
    void unauthorizedResponseDoesNotCarryWwwAuthenticateHeader() throws Exception {
        InternalSecretFilter filter = new InternalSecretFilter("super-secret");
        MockHttpServletRequest req = new MockHttpServletRequest(
            "GET", "/api/v1/internal/reasoning-context/AAPL");
        req.addHeader("X-Internal-Secret", "WRONG");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertEquals(null, res.getHeader("WWW-Authenticate"),
            "InternalSecretFilter must not set WWW-Authenticate — its presence in prod logs is a fingerprint of Spring Security, not this filter");
    }

    @Test
    void unauthorizedResponseBodyMatchesProdLogFingerprint() throws Exception {
        InternalSecretFilter filter = new InternalSecretFilter("super-secret");
        MockHttpServletRequest req = new MockHttpServletRequest(
            "GET", "/api/v1/internal/reasoning-context/AAPL");
        // No header at all — empty case (still 401, same error message).
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        // sendError(status, message) stores the message in getErrorMessage() —
        // that is the value Spring logs as the ERROR_MESSAGE MDC field in prod
        // (and what shows up in your "Missing or invalid X-Internal-Secret"
        // log fingerprint). MockHttpServletResponse does NOT serialise it into
        // the body the way the real container does; the fingerprint lives in
        // getErrorMessage(). Spring Security's 401 carries a different message
        // and a WWW-Authenticate header — both absent here.
        assertEquals("Missing or invalid X-Internal-Secret", res.getErrorMessage(),
            "If prod logs show this body, the 401 originates from InternalSecretFilter");
    }
}
