package com.tradingsaas.marketdata.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.SymbolEntity;
import com.tradingsaas.marketdata.adapter.out.persistence.entity.TechnicalIndicatorEntity;
import com.tradingsaas.marketdata.adapter.out.persistence.mapper.TechnicalIndicatorEntityMapper;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TechnicalIndicatorRepositoryAdapterTest {

    private TechnicalIndicatorJpaRepository jpaRepository;
    private TechnicalIndicatorEntityMapper mapper;
    private TechnicalIndicatorRepositoryAdapter adapter;

    private final Symbol symbol = new Symbol("AAPL", "Apple Inc.", "NASDAQ");
    private final LocalDate date = LocalDate.of(2026, 4, 16);

    @BeforeEach
    void setUp() {
        jpaRepository = mock(TechnicalIndicatorJpaRepository.class);
        mapper = mock(TechnicalIndicatorEntityMapper.class);
        adapter = new TechnicalIndicatorRepositoryAdapter(jpaRepository, mapper);
    }

    @Test
    void saveAllPersistsAndReturnsMappedIndicators() {
        TechnicalIndicator domain = rsi(symbol, date, "62.5");
        TechnicalIndicatorEntity entity = entity(symbol, date, TechnicalIndicatorType.RSI, "62.5");

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpaRepository.saveAll(List.of(entity))).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        List<TechnicalIndicator> result = adapter.saveAll(List.of(domain));

        assertEquals(List.of(domain), result);
        verify(jpaRepository).saveAll(List.of(entity));
        verifyNoMoreInteractions(jpaRepository);
    }

    @Test
    void findBySymbolAndDateReturnsMappedResults() {
        TechnicalIndicator domain = rsi(symbol, date, "55.0");
        TechnicalIndicatorEntity entity = entity(symbol, date, TechnicalIndicatorType.RSI, "55.0");

        when(jpaRepository.findBySymbolTickerAndDate("AAPL", date)).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        List<TechnicalIndicator> result = adapter.findBySymbolAndDate(symbol, date);

        assertEquals(List.of(domain), result);
        verify(jpaRepository).findBySymbolTickerAndDate("AAPL", date);
        verifyNoMoreInteractions(jpaRepository);
    }

    @Test
    void findBySymbolAndDateRangeReturnsMappedResults() {
        LocalDate from = date.minusDays(5);
        TechnicalIndicator domain = rsi(symbol, date, "48.3");
        TechnicalIndicatorEntity entity = entity(symbol, date, TechnicalIndicatorType.RSI, "48.3");

        when(jpaRepository.findBySymbolTickerAndDateBetween("AAPL", from, date)).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        List<TechnicalIndicator> result = adapter.findBySymbolAndDateRange(symbol, from, date);

        assertEquals(List.of(domain), result);
        verify(jpaRepository).findBySymbolTickerAndDateBetween("AAPL", from, date);
        verifyNoMoreInteractions(jpaRepository);
    }

    @Test
    void findBySymbolAndDateAndTypeReturnsMappedOptional() {
        TechnicalIndicator domain = rsi(symbol, date, "70.1");
        TechnicalIndicatorEntity entity = entity(symbol, date, TechnicalIndicatorType.RSI, "70.1");

        when(jpaRepository.findBySymbolTickerAndDateAndType("AAPL", date, TechnicalIndicatorType.RSI))
                .thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        Optional<TechnicalIndicator> result =
                adapter.findBySymbolAndDateAndType(symbol, date, TechnicalIndicatorType.RSI);

        assertTrue(result.isPresent());
        assertEquals(domain, result.get());
        verify(jpaRepository).findBySymbolTickerAndDateAndType("AAPL", date, TechnicalIndicatorType.RSI);
        verifyNoMoreInteractions(jpaRepository);
    }

    @Test
    void findBySymbolAndDateAndTypeReturnsEmptyWhenNotFound() {
        when(jpaRepository.findBySymbolTickerAndDateAndType("AAPL", date, TechnicalIndicatorType.MACD))
                .thenReturn(Optional.empty());

        Optional<TechnicalIndicator> result =
                adapter.findBySymbolAndDateAndType(symbol, date, TechnicalIndicatorType.MACD);

        assertTrue(result.isEmpty());
    }

    private static TechnicalIndicator rsi(Symbol symbol, LocalDate date, String value) {
        return new TechnicalIndicator(symbol, date, TechnicalIndicatorType.RSI, new BigDecimal(value), Map.of());
    }

    private static TechnicalIndicatorEntity entity(Symbol symbol, LocalDate date, TechnicalIndicatorType type, String value) {
        SymbolEntity symbolEntity = new SymbolEntity(symbol.ticker(), symbol.name(), symbol.exchange(), symbol.sector(), symbol.active());
        return new TechnicalIndicatorEntity(symbolEntity, date, type, new BigDecimal(value), Map.of());
    }
}
