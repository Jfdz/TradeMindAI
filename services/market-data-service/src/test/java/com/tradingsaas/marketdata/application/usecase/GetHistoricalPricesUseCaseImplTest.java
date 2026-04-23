package com.tradingsaas.marketdata.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class GetHistoricalPricesUseCaseImplTest {

    private final StockPriceRepository repository = mock(StockPriceRepository.class);
    private final GetHistoricalPricesUseCaseImpl useCase = new GetHistoricalPricesUseCaseImpl(repository);

    @Test
    void normalizesTickerAndDelegatesToRepository() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 4, 16);
        PageRequest pageable = PageRequest.of(0, 20);

        StockPrice price = price("AAPL", to);
        Page<StockPrice> expected = new PageImpl<>(List.of(price), pageable, 1);

        when(repository.findHistoricalDataPaged("AAPL", TimeFrame.DAILY, from, to, pageable))
                .thenReturn(expected);

        Page<StockPrice> result = useCase.getHistoricalPrices("aapl", TimeFrame.DAILY, from, to, pageable);

        assertEquals(expected, result);
        verify(repository).findHistoricalDataPaged("AAPL", TimeFrame.DAILY, from, to, pageable);
    }

    private static StockPrice price(String ticker, LocalDate date) {
        Symbol symbol = new Symbol(ticker, "Apple Inc.", "NASDAQ");
        OHLCV ohlcv = new OHLCV(new BigDecimal("170"), new BigDecimal("175"),
                new BigDecimal("168"), new BigDecimal("172"), 1_000_000L);
        return new StockPrice(symbol, date, TimeFrame.DAILY, ohlcv);
    }
}
