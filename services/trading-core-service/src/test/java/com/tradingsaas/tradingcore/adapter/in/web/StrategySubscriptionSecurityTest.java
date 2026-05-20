package com.tradingsaas.tradingcore.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.in.web.dto.StrategyRequest;
import com.tradingsaas.tradingcore.config.SecurityConfig;
import com.tradingsaas.tradingcore.domain.exception.InsufficientSubscriptionException;
import com.tradingsaas.tradingcore.domain.model.RiskParameters;
import com.tradingsaas.tradingcore.domain.model.Strategy;
import com.tradingsaas.tradingcore.domain.model.Subscription;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.port.in.ManageStrategiesUseCase;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import com.tradingsaas.tradingcore.application.usecase.SubscriptionUsageLedgerService;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import java.math.BigDecimal;
import java.time.Instant;
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

@WebMvcTest(controllers = StrategyController.class)
@Import({SecurityConfig.class, SubscriptionUsageLedgerWebConfig.class})
@TestPropertySource(properties = {
        "trading-core.cors.allowed-origins=https://trading-saas.example.com",
        "trading-core.rate-limit.free-per-minute=2",
        "trading-core.rate-limit.basic-per-minute=5",
        "trading-core.rate-limit.premium-per-minute=10",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://clerk.example.dev",
        "trading-core.clerk.audience=https://api.trademindai.com"
})
class StrategySubscriptionSecurityTest {

    private static final UUID FREE_USER_ID  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BASIC_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManageStrategiesUseCase manageStrategiesUseCase;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserRepository userRepository;

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
    void freePlanGetsPaymentRequiredWhenStrategyLimitReached() throws Exception {
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(ConsumptionProbe.consumed(1, 1));
        when(manageStrategiesUseCase.createStrategy(any(), anyString(), any()))
                .thenThrow(new InsufficientSubscriptionException(SubscriptionPlan.BASIC));

        mockMvc.perform(post("/api/v1/strategies")
                        .with(jwt().jwt(j -> j.subject("clerk_free").claim("email", "user@example.com")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(strategyRequest())))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.requiredTier").value("basic"))
                .andExpect(jsonPath("$.path").value("/api/v1/strategies"))
                .andExpect(header().string("X-RateLimit-Limit", "2"));

        verify(subscriptionUsageLedgerService).record(
                new TokenClaims(FREE_USER_ID, "user@example.com", "FREE"),
                "strategy_create",
                "POST",
                "/api/v1/strategies",
                402);
    }

    @Test
    void basicPlanCanCreateStrategy() throws Exception {
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(ConsumptionProbe.consumed(4, 1));
        when(manageStrategiesUseCase.createStrategy(any(), anyString(), any()))
                .thenReturn(strategy());

        mockMvc.perform(post("/api/v1/strategies")
                        .with(jwt().jwt(j -> j.subject("clerk_basic").claim("email", "basic@example.com")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(strategyRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Trend Following"))
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "4"));

        verify(manageStrategiesUseCase).createStrategy(any(), anyString(), any());
        verify(subscriptionUsageLedgerService).record(
                new TokenClaims(BASIC_USER_ID, "basic@example.com", "BASIC"),
                "strategy_create",
                "POST",
                "/api/v1/strategies",
                201);
    }

    @Test
    void unauthenticatedStrategyCreateIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/strategies")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(strategyRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verify(manageStrategiesUseCase, never()).createStrategy(any(), anyString(), any());
    }

    private StrategyRequest strategyRequest() {
        return new StrategyRequest(
                "Trend Following",
                "desc",
                new BigDecimal("2.00"),
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                true
        );
    }

    private Strategy strategy() {
        return new Strategy(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Trend Following",
                "desc",
                new RiskParameters(new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("10.00")),
                true,
                Instant.parse("2026-04-17T10:00:00Z"),
                Instant.parse("2026-04-17T10:00:00Z")
        );
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
