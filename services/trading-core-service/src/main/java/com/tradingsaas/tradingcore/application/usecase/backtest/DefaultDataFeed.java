package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.exception.InsufficientMarketDataException;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
class DefaultDataFeed implements DataFeed {

    private final HistoricalMarketDataPort historicalMarketDataPort;

    DefaultDataFeed(HistoricalMarketDataPort historicalMarketDataPort) {
        this.historicalMarketDataPort = historicalMarketDataPort;
    }

    @Override
    public Iterator<OhlcvBar> open(String symbol, LocalDate from, LocalDate to) {
        List<OhlcvBar> sortedBars = historicalMarketDataPort.loadHistoricalBars(symbol, from, to).stream()
                .sorted(Comparator.comparing(OhlcvBar::timestamp))
                .toList();

        if (sortedBars.isEmpty()) {
            throw new InsufficientMarketDataException(
                    "No price data found for " + symbol + " between " + from + " and " + to +
                    ". Please select a symbol and date range with available market data.");
        }

        return new Cursor(sortedBars);
    }

    private static final class Cursor implements Iterator<OhlcvBar> {
        private final List<OhlcvBar> bars;
        private int index;

        private Cursor(List<OhlcvBar> bars) {
            this.bars = List.copyOf(bars);
        }

        @Override
        public boolean hasNext() {
            return index < bars.size();
        }

        @Override
        public OhlcvBar next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more historical bars available");
            }

            return bars.get(index++);
        }
    }
}
