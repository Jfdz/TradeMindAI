package com.tradingsaas.marketdata.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalSecretFilterTest {

    @Test
    void rejectsInternalApiRequestWithoutSecret() throws ServletException, IOException {
        InternalSecretFilter filter = new InternalSecretFilter("test-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/prices/AAPL/latest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void continuesInternalApiRequestWithValidSecret() throws ServletException, IOException {
        InternalSecretFilter filter = new InternalSecretFilter("test-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/prices/AAPL/latest");
        request.addHeader("X-Internal-Secret", "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }
}
