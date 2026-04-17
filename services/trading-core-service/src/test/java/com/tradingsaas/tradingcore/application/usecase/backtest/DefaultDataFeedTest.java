package com.tradingsaas.tradingcore.application.usecase.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class DefaultDataFeedTest {

    @Test
    void openShouldReplayBarsChronologically() {
        HistoricalMarketDataPort marketDataPort = mock(HistoricalMarketDataPort.class);
        DefaultDataFeed dataFeed = new DefaultDataFeed(marketDataPort);

        OhlcvBar first = new OhlcvBar(Instant.parse("2026-04-16T10:00:00Z"), 100, 104, 99, 103, 1_500);
        OhlcvBar second = new OhlcvBar(Instant.parse("2026-04-15T10:00:00Z"), 95, 101, 94, 100, 1_250);
        OhlcvBar third = new OhlcvBar(Instant.parse("2026-04-17T10:00:00Z"), 103, 108, 102, 107, 1_900);

        when(marketDataPort.loadHistoricalBars("AAPL", LocalDate.of(2026, 4, 15), LocalDate.of(2026, 4, 17)))
                .thenReturn(List.of(first, second, third));

        Iterator<OhlcvBar> cursor = dataFeed.open("AAPL", LocalDate.of(2026, 4, 15), LocalDate.of(2026, 4, 17));

        assertEquals(second, cursor.next());
        assertEquals(first, cursor.next());
        assertEquals(third, cursor.next());
        assertFalse(cursor.hasNext());
        assertThrows(NoSuchElementException.class, cursor::next);
    }

    @Test
    void openShouldReturnEmptyCursorForNoData() {
        HistoricalMarketDataPort marketDataPort = mock(HistoricalMarketDataPort.class);
        DefaultDataFeed dataFeed = new DefaultDataFeed(marketDataPort);

        when(marketDataPort.loadHistoricalBars("MSFT", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2)))
                .thenReturn(List.of());

        Iterator<OhlcvBar> cursor = dataFeed.open("MSFT", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2));

        assertFalse(cursor.hasNext());
        assertThrows(NoSuchElementException.class, cursor::next);
    }
}
