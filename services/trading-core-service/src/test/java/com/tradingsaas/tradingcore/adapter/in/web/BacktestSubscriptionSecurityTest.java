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
import com.tradingsaas.tradingcore.adapter.in.web.aspect.RequiresSubscriptionAspect;
import com.tradingsaas.tradingcore.application.usecase.backtest.BacktestExecutionService;
import com.tradingsaas.tradingcore.application.usecase.SubscriptionUsageLedgerService;
import com.tradingsaas.tradingcore.config.SecurityConfig;
import com.tradingsaas.tradingcore.domain.model.Subscription;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestJob;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestRequest;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestStatus;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BacktestController.class)
@Import({
        SecurityConfig.class,
        RequiresSubscriptionAspect.class,
        SubscriptionUsageLedgerWebConfig.class,
        BacktestSubscriptionSecurityTest.TestAopConfig.class
})
@TestPropertySource(properties = {
        "trading-core.cors.allowed-origins=https://trading-saas.example.com",
        "trading-core.rate-limit.free-per-minute=2",
        "trading-core.rate-limit.basic-per-minute=5",
        "trading-core.rate-limit.premium-per-minute=10",
        "subscriptions.enforce=true",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://clerk.example.dev",
        "trading-core.clerk.audience=https://api.trademindai.com"
})
class BacktestSubscriptionSecurityTest {

    private static final UUID FREE_USER_ID  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BASIC_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TestConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestAopConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BacktestExecutionService backtestExecutionService;

    @MockBean
    private HistoricalMarketDataPort historicalMarketDataPort;

    @MockBean
    private SubscriptionUsageLedgerService subscriptionUsageLedgerService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserRepository userRepository;

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
    void freePlanCannotSubmitBacktest() throws Exception {
        when(bucket.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.consumed(1, 1));

        mockMvc.perform(post("/api/v1/backtests")
                        .with(jwt().jwt(j -> j.subject("clerk_free").claim("email", "user@example.com")))
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.requiredTier").value("basic"))
                .andExpect(jsonPath("$.path").value("/api/v1/backtests"))
                .andExpect(header().string("X-RateLimit-Limit", "2"));

        verify(backtestExecutionService, never()).submit(any(BacktestRequest.class));
        verify(subscriptionUsageLedgerService).record(
                new TokenClaims(FREE_USER_ID, "user@example.com", "FREE"),
                "backtest_submit",
                "POST",
                "/api/v1/backtests",
                402);
    }

    @Test
    void basicPlanCanSubmitBacktest() throws Exception {
        UUID jobId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(bucket.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.consumed(4, 1));
        when(backtestExecutionService.submit(any(BacktestRequest.class))).thenReturn(jobId);
        when(backtestExecutionService.getJob(jobId)).thenReturn(new BacktestJob(
                jobId,
                new BacktestRequest("AAPL", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 5), 10),
                BacktestStatus.PENDING,
                null,
                Instant.parse("2026-04-29T12:00:00Z"),
                Instant.parse("2026-04-29T12:00:00Z"),
                null
        ));

        mockMvc.perform(post("/api/v1/backtests")
                        .with(jwt().jwt(j -> j.subject("clerk_basic").claim("email", "basic@example.com")))
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "4"));

        verify(subscriptionUsageLedgerService).record(
                new TokenClaims(BASIC_USER_ID, "basic@example.com", "BASIC"),
                "backtest_submit",
                "POST",
                "/api/v1/backtests",
                202);
    }

    private String requestJson() throws Exception {
        return objectMapper.writeValueAsString(new BacktestController.BacktestSubmissionRequest(
                "AAPL",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 5),
                10
        ));
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
