package com.tradingsaas.marketdata.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.StockPriceEntity;
import com.tradingsaas.marketdata.adapter.out.persistence.entity.SymbolEntity;
import com.tradingsaas.marketdata.adapter.out.persistence.mapper.StockPriceEntityMapper;
import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class StockPriceRepositoryAdapterTest {

    @Test
    void saveAllUpsertsExistingDailyBarInsteadOfCreatingDuplicate() {
        StockPriceJpaRepository jpaRepository = Mockito.mock(StockPriceJpaRepository.class);
        StockPriceEntityMapper mapper = Mockito.mock(StockPriceEntityMapper.class);
        StockPriceRepositoryAdapter adapter = new StockPriceRepositoryAdapter(jpaRepository, mapper);

        StockPrice domain = new StockPrice(
                new Symbol("AAPL", "Apple Inc.", "NASDAQ", "Technology", true),
                LocalDate.of(2026, 4, 29),
                TimeFrame.DAILY,
                new OHLCV(
                        new BigDecimal("170.00"),
                        new BigDecimal("175.00"),
                        new BigDecimal("169.00"),
                        new BigDecimal("174.00"),
                        1_000L),
                new BigDecimal("174.50"));

        SymbolEntity symbol = new SymbolEntity("AAPL", "Apple Inc.", "NASDAQ", "Technology", true);
        StockPriceEntity mapped = new StockPriceEntity(
                symbol,
                LocalDate.of(2026, 4, 29),
                TimeFrame.DAILY,
                new BigDecimal("170.00"),
                new BigDecimal("175.00"),
                new BigDecimal("169.00"),
                new BigDecimal("174.00"),
                new BigDecimal("174.50"),
                1_000L);
        StockPriceEntity existing = new StockPriceEntity(
                symbol,
                LocalDate.of(2026, 4, 29),
                TimeFrame.DAILY,
                new BigDecimal("160.00"),
                new BigDecimal("161.00"),
                new BigDecimal("159.00"),
                new BigDecimal("160.50"),
                new BigDecimal("160.50"),
                800L);
        existing.setId(42L);

        when(mapper.toEntity(domain)).thenReturn(mapped);
        when(jpaRepository.findBySymbol_TickerAndDateAndTimeFrame("AAPL", LocalDate.of(2026, 4, 29), TimeFrame.DAILY))
                .thenReturn(Optional.of(existing));
        when(jpaRepository.saveAll(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toDomain(existing)).thenReturn(domain);

        List<StockPrice> result = adapter.saveAll(List.of(domain));

        ArgumentCaptor<List<StockPriceEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(jpaRepository).saveAll(captor.capture());
        StockPriceEntity saved = captor.getValue().getFirst();
        assertEquals(42L, saved.getId());
        assertEquals(new BigDecimal("174.50"), saved.getAdjustedClose());
        assertEquals(1_000L, saved.getVolume());
        assertEquals(1, result.size());
    }
}
