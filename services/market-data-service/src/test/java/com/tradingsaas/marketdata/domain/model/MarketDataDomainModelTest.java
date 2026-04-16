package com.tradingsaas.marketdata.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketDataDomainModelTest {

    @Test
    void symbolNormalizesRequiredFields() {
        Symbol symbol = new Symbol(" aapl ", " Apple Inc. ", " nasdaq ", " Technology ", true);

        assertEquals("AAPL", symbol.ticker());
        assertEquals("Apple Inc.", symbol.name());
        assertEquals("NASDAQ", symbol.exchange());
        assertEquals("Technology", symbol.sector());
        assertTrue(symbol.isActive());
    }

    @Test
    void symbolRejectsBlankTicker() {
        assertThrows(IllegalArgumentException.class, () -> new Symbol("   ", "Apple", "NASDAQ"));
    }

    @Test
    void ohlcvRejectsInvalidPriceRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OHLCV(
                        BigDecimal.TEN,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(2),
                        BigDecimal.ONE,
                        10L));
    }

    @Test
    void stockPriceDefaultsAdjustedCloseToBarClose() {
        Symbol symbol = new Symbol("AAPL", "Apple Inc.", "NASDAQ");
        OHLCV bar = new OHLCV(
                new BigDecimal("10.00"),
                new BigDecimal("12.00"),
                new BigDecimal("9.50"),
                new BigDecimal("11.50"),
                1_000L);

        StockPrice stockPrice = new StockPrice(symbol, LocalDate.of(2026, 4, 16), TimeFrame.DAILY, bar);

        assertEquals(new BigDecimal("11.50"), stockPrice.adjustedClose());
    }

    @Test
    void technicalIndicatorCopiesMetadataAndRejectsMutation() {
        Symbol symbol = new Symbol("AAPL", "Apple Inc.", "NASDAQ");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("period", "14");

        TechnicalIndicator indicator = new TechnicalIndicator(
                symbol,
                LocalDate.of(2026, 4, 16),
                TechnicalIndicatorType.RSI,
                new BigDecimal("55.2"),
                metadata);

        assertEquals("14", indicator.metadata().get("period"));
        assertThrows(UnsupportedOperationException.class, () -> indicator.metadata().put("extra", "value"));
        assertFalse(indicator.metadata().isEmpty());
    }

    @Test
    void timeframeExposesApiValueAndIntradayFlag() {
        assertEquals("1d", TimeFrame.DAILY.apiValue());
        assertFalse(TimeFrame.DAILY.isIntraday());
        assertTrue(TimeFrame.MINUTE_5.isIntraday());
    }
}
