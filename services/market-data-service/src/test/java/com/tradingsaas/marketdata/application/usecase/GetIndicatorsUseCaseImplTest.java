package com.tradingsaas.marketdata.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import com.tradingsaas.marketdata.domain.port.out.TechnicalIndicatorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GetIndicatorsUseCaseImplTest {

    private final TechnicalIndicatorRepository repository = mock(TechnicalIndicatorRepository.class);
    private final GetIndicatorsUseCaseImpl useCase = new GetIndicatorsUseCaseImpl(repository);

    @Test
    void returnsIndicatorsFromRepository() {
        Symbol symbol = new Symbol("AAPL", "Apple Inc.", "NASDAQ");
        TechnicalIndicator rsi = new TechnicalIndicator(
                symbol, LocalDate.of(2026, 4, 16), TechnicalIndicatorType.RSI, new BigDecimal("62.5"), Map.of());

        when(repository.findLatestByTicker("AAPL", List.of(TechnicalIndicatorType.RSI))).thenReturn(List.of(rsi));

        List<TechnicalIndicator> result =
                useCase.getLatestIndicators("AAPL", List.of(TechnicalIndicatorType.RSI));

        assertEquals(List.of(rsi), result);
        verify(repository).findLatestByTicker("AAPL", List.of(TechnicalIndicatorType.RSI));
    }

    @Test
    void passesEmptyListWhenTypesIsNull() {
        when(repository.findLatestByTicker("AAPL", List.of())).thenReturn(List.of());

        List<TechnicalIndicator> result = useCase.getLatestIndicators("AAPL", null);

        assertTrue(result.isEmpty());
        verify(repository).findLatestByTicker("AAPL", List.of());
    }

    @Test
    void returnsEmptyListWhenRepositoryReturnsNone() {
        when(repository.findLatestByTicker("UNKNOWN", List.of())).thenReturn(List.of());

        List<TechnicalIndicator> result = useCase.getLatestIndicators("UNKNOWN", List.of());

        assertTrue(result.isEmpty());
    }
}
