package com.tradingsaas.marketdata.adapter.out.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.StockPriceEntity;
import com.tradingsaas.marketdata.adapter.out.persistence.entity.SymbolEntity;
import com.tradingsaas.marketdata.adapter.out.persistence.entity.TechnicalIndicatorEntity;
import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MarketDataPersistenceMapperTest {

    private final SymbolEntityMapper symbolMapper = Mappers.getMapper(SymbolEntityMapper.class);
    private final StockPriceEntityMapper stockPriceMapper = Mappers.getMapper(StockPriceEntityMapper.class);
    private final TechnicalIndicatorEntityMapper technicalIndicatorMapper =
            Mappers.getMapper(TechnicalIndicatorEntityMapper.class);

    @Test
    void symbolMapperRoundTripsDomainModel() {
        Symbol source = new Symbol("AAPL", "Apple Inc.", "NASDAQ", "Technology", true);

        SymbolEntity entity = symbolMapper.toEntity(source);
        Symbol roundTrip = symbolMapper.toDomain(entity);

        assertEquals(source, roundTrip);
        assertEquals("AAPL", entity.getTicker());
    }

    @Test
    void stockPriceMapperRoundTripsDomainModel() {
        StockPrice source = new StockPrice(
                new Symbol("AAPL", "Apple Inc.", "NASDAQ"),
                LocalDate.of(2026, 4, 16),
                TimeFrame.DAILY,
                new OHLCV(
                        new BigDecimal("10.00"),
                        new BigDecimal("12.00"),
                        new BigDecimal("9.50"),
                        new BigDecimal("11.50"),
                        1_000L),
                new BigDecimal("11.40"));

        StockPriceEntity entity = stockPriceMapper.toEntity(source);
        assertNotNull(entity.getSymbol());

        StockPrice roundTrip = stockPriceMapper.toDomain(entity);

        assertEquals(source, roundTrip);
        assertEquals(TimeFrame.DAILY, entity.getTimeFrame());
    }

    @Test
    void technicalIndicatorMapperRoundTripsDomainModel() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("period", "14");
        metadata.put("source", "ta4j");

        TechnicalIndicator source = new TechnicalIndicator(
                new Symbol("AAPL", "Apple Inc.", "NASDAQ"),
                LocalDate.of(2026, 4, 16),
                TechnicalIndicatorType.RSI,
                new BigDecimal("55.2"),
                metadata);

        TechnicalIndicatorEntity entity = technicalIndicatorMapper.toEntity(source);
        TechnicalIndicator roundTrip = technicalIndicatorMapper.toDomain(entity);

        assertEquals(source, roundTrip);
        assertEquals(TechnicalIndicatorType.RSI, entity.getType());
    }
}
