package com.tradingsaas.tradingcore.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tradingsaas.tradingcore.adapter.in.web.MarketDataProxyController;
import com.tradingsaas.tradingcore.adapter.in.web.SubscriptionAccessGuard;
import com.tradingsaas.tradingcore.adapter.in.web.SubscriptionUsageLedgerWebConfig;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketPriceResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketSymbolPageResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketSymbolResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.Ohlcv;
import com.tradingsaas.tradingcore.application.usecase.SubscriptionUsageLedgerService;
import com.tradingsaas.tradingcore.domain.exception.InsufficientSubscriptionException;
import com.tradingsaas.tradingcore.domain.model.Subscription;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MarketDataProxyController.class)
@Import({SecurityConfig.class, SubscriptionUsageLedgerWebConfig.class})
@TestPropertySource(properties = {
        "trading-core.cors.allowed-origins=https://trading-saas.example.com",
        "trading-core.rate-limit.free-per-minute=2",
        "trading-core.rate-limit.basic-per-minute=5",
        "trading-core.rate-limit.premium-per-minute=10",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://clerk.example.dev",
        "trading-core.clerk.audience=https://api.trademind.es"
})
class MarketDataProxySecurityTest {

    private static final UUID FREE_USER_ID  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BASIC_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDataServiceAdapter marketDataServiceAdapter;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private SubscriptionAccessGuard subscriptionAccessGuard;

    @MockBean
    private SubscriptionUsageLedgerService subscriptionUsageLedgerService;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private LettuceBasedProxyManager<String> rateLimitProxyManager;

    private final BucketProxy bucket = mock(BucketProxy.class);

    @BeforeEach
    void setUp() {
        when(rateLimitProxyManager.builder().build(anyString(), org.mockito.ArgumentMatchers.<Supplier<BucketConfiguration>>any()))
                .thenReturn(bucket);
        when(userRepository.findByClerkUserId("clerk_free"))
                .thenReturn(Optional.of(freeUser()));
        when(userRepository.findByClerkUserId("clerk_basic"))
                .thenReturn(Optional.of(basicUser()));
    }

    @Test
    void latestPriceIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/prices/AAPL/latest"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401) throw new AssertionError("Expected non-401 for public prices endpoint, got 401");
                });
    }

    @Test
    void symbolsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/symbols"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/v1/symbols"));
    }

    @Test
    void latestPriceReturnsRateLimitHeadersForAuthenticatedRequest() throws Exception {
        when(bucket.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.consumed(1, 1));
        when(marketDataServiceAdapter.fetchLatestPrice("AAPL", "DAILY"))
                .thenReturn(Optional.of(new MarketPriceResponse(
                        "AAPL",
                        LocalDate.of(2026, 4, 29),
                        "DAILY",
                        new Ohlcv(170.0, 175.0, 169.0, 174.0, 1000),
                        new BigDecimal("174.0"))));

        mockMvc.perform(get("/api/v1/prices/AAPL/latest")
                        .with(jwt().jwt(j -> j.subject("clerk_free").claim("email", "user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"))
                .andExpect(jsonPath("$.ticker").value("AAPL"));
    }

    @Test
    void symbolsReturn429WhenRateLimitIsExceeded() throws Exception {
        when(bucket.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.rejected(0, 60_000_000_000L, 60_000_000_000L));

        mockMvc.perform(get("/api/v1/symbols")
                        .with(jwt().jwt(j -> j.subject("clerk_free").claim("email", "user@example.com"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.path").value("/api/v1/symbols"));

        verify(marketDataServiceAdapter, never()).fetchSymbols(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void symbolsUseProxyAfterAuthenticationAndRateLimitPass() throws Exception {
        when(bucket.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.consumed(1, 1));
        when(marketDataServiceAdapter.fetchSymbols(0, 20))
                .thenReturn(new MarketSymbolPageResponse(
                        List.of(new MarketSymbolResponse("AAPL", "Apple Inc.", "NASDAQ", "Technology", true)),
                        0,
                        20,
                        1,
                        1));

        mockMvc.perform(get("/api/v1/symbols")
                        .with(jwt().jwt(j -> j.subject("clerk_free").claim("email", "user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticker").value("AAPL"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
    }

    @Test
    void freePlanCannotRequestMoreThanOneYearOfHistory() throws Exception {
        when(bucket.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.consumed(1, 1));
        doThrow(new InsufficientSubscriptionException(SubscriptionPlan.BASIC))
                .when(subscriptionAccessGuard)
                .requireHistoricalPriceAccess(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 4, 29));

        mockMvc.perform(get("/api/v1/prices/AAPL/history")
                        .param("from", "2020-01-01")
                        .param("to", "2026-04-29")
                        .with(jwt().jwt(j -> j.subject("clerk_free").claim("email", "user@example.com"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.requiredTier").value("basic"))
                .andExpect(jsonPath("$.path").value("/api/v1/prices/AAPL/history"));

        verify(subscriptionUsageLedgerService).record(
                new TokenClaims(FREE_USER_ID, "user@example.com", "FREE"),
                "historical_prices",
                "GET",
                "/api/v1/prices/AAPL/history",
                402);
    }

    @Test
    void basicPlanCanRequestMoreThanOneYearOfHistory() throws Exception {
        when(bucket.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.consumed(4, 1));
        doNothing().when(subscriptionAccessGuard)
                .requireHistoricalPriceAccess(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 4, 29));
        when(marketDataServiceAdapter.fetchHistoricalPrices(
                "AAPL",
                "DAILY",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2026, 4, 29),
                0,
                20))
                .thenReturn(new MarketDataServiceAdapter.MarketPricePageResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/prices/AAPL/history")
                        .param("from", "2020-01-01")
                        .param("to", "2026-04-29")
                        .with(jwt().jwt(j -> j.subject("clerk_basic").claim("email", "basic@example.com"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "5"));

        verify(subscriptionUsageLedgerService).record(
                new TokenClaims(BASIC_USER_ID, "basic@example.com", "BASIC"),
                "historical_prices",
                "GET",
                "/api/v1/prices/AAPL/history",
                200);
    }

    private static User freeUser() {
        Instant now = Instant.parse("2026-04-01T00:00:00Z");
        Subscription sub = new Subscription(UUID.randomUUID(), FREE_USER_ID, SubscriptionPlan.FREE, now, null);
        return new User(FREE_USER_ID, "clerk_free", "user@example.com", null,
                "Free", "User", "UTC", sub, now, true);
    }

    private static User basicUser() {
        Instant now = Instant.parse("2026-04-01T00:00:00Z");
        Subscription sub = new Subscription(UUID.randomUUID(), BASIC_USER_ID, SubscriptionPlan.BASIC, now, null);
        return new User(BASIC_USER_ID, "clerk_basic", "basic@example.com", null,
                "Basic", "User", "UTC", sub, now, true);
    }
}
