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
}
