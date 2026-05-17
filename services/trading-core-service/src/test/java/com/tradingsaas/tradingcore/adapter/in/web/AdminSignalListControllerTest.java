package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.in.web.dto.AdminSignalSummary;
import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AdminSignalListControllerTest {

    private final TradingSignalRepository repository = mock(TradingSignalRepository.class);
    private final AdminSignalListController controller = new AdminSignalListController(repository);

    private static TradingSignal signal(UUID id, String ticker, ReasoningArtifact artifact) {
        return new TradingSignal(
                id, UUID.randomUUID(), ticker, SignalType.BUY,
                new Confidence(new BigDecimal("0.6200")), Timeframe.DAILY,
                Instant.parse("2026-05-13T12:00:00Z"),
                null, null, new BigDecimal("4.5"), new BigDecimal("603.0"),
                new BigDecimal("615.0"), null, null,
                "Price 603.0 above sma_200 (510.0).",
                ReasoningStatus.READY,
                Instant.parse("2026-05-13T12:00:30Z"),
                artifact);
    }

    private static ReasoningArtifact artifact() {
        return new ReasoningArtifact(
                "GENERATED", "anthropic_oauth", "claude-haiku-4-5", 0, null,
                Map.of("ticker", "META"),
                List.of("sma_200"),
                List.of("https://reuters.com/x"),
                List.of(),
                Map.of("input_tokens", 100));
    }

    @Test
    void listReturnsPagedSummariesWhenNoTickerFilter() {
        UUID id = UUID.randomUUID();
        when(repository.findAdminSignals(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(signal(id, "META", artifact()))));

        ResponseEntity<Page<AdminSignalSummary>> response = controller.list(null, 0, 25);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Page<AdminSignalSummary> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getContent().size());
        AdminSignalSummary row = body.getContent().get(0);
        assertEquals(id, row.id());
        assertEquals("META", row.ticker());
        assertEquals(new BigDecimal("603.0"), row.entryPrice());
        assertEquals(new BigDecimal("615.0"), row.targetPrice());
        assertEquals("GENERATED", row.reasoningOutcome());
        assertEquals("anthropic_oauth", row.reasoningProvider());
        assertTrue(row.hasArtifact());
    }

    @Test
    void listForwardsTickerFilterToRepository() {
        when(repository.findAdminSignals(eq("AAPL"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.list("AAPL", 0, 25);

        verify(repository).findAdminSignals(eq("AAPL"), any(Pageable.class));
    }

    @Test
    void listClampsPageSizeToMaximum() {
        when(repository.findAdminSignals(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.list(null, 0, 9999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAdminSignals(any(), captor.capture());
        assertEquals(AdminSignalListController.MAX_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void listClampsNegativePageToZero() {
        when(repository.findAdminSignals(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.list(null, -5, 25);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAdminSignals(any(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
    }

    @Test
    void listReportsHasArtifactFalseWhenSignalHasNoArtifact() {
        UUID id = UUID.randomUUID();
        when(repository.findAdminSignals(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(signal(id, "META", null))));

        ResponseEntity<Page<AdminSignalSummary>> response = controller.list(null, 0, 25);

        AdminSignalSummary row = response.getBody().getContent().get(0);
        assertEquals(false, row.hasArtifact());
        assertEquals(null, row.reasoningOutcome());
        assertEquals(null, row.reasoningProvider());
        assertEquals(null, row.reasoningRetryCount());
    }

    @Test
    void tickersReturnsDistinctList() {
        when(repository.findDistinctTickers()).thenReturn(List.of("AAPL", "META", "NVDA"));

        ResponseEntity<List<String>> response = controller.tickers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("AAPL", "META", "NVDA"), response.getBody());
    }
}
