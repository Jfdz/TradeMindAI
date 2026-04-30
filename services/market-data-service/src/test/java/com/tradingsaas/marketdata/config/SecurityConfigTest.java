package com.tradingsaas.marketdata.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, InternalSecretFilter.class})
@TestPropertySource(properties = {
        "market-data.cors.allowed-origins=https://trading-saas.example.com",
        "market-data.internal-secret=test-secret"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void addsSecurityHeadersOnResponses() throws Exception {
        mockMvc.perform(get("/test").secure(true).accept(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().string("Strict-Transport-Security", org.hamcrest.Matchers.containsString("max-age=31536000")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")));
    }

    @Test
    void allowsConfiguredCorsOrigin() throws Exception {
        mockMvc.perform(options("/test")
                        .header("Origin", "https://trading-saas.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://trading-saas.example.com"));
    }

    @Test
    void rejectsInternalApiWithoutSecret() throws Exception {
        mockMvc.perform(get("/api/v1/test").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsInternalApiWithSecret() throws Exception {
        mockMvc.perform(get("/api/v1/test")
                        .header("X-Internal-Secret", "test-secret")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @RestController
    static class TestController {
        @GetMapping("/test")
        String test() {
            return "ok";
        }

        @GetMapping("/api/v1/test")
        String internalApi() {
            return "ok";
        }
    }
}
