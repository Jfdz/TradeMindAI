package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.in.GetHistoricalPricesUseCase;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class GetHistoricalPricesUseCaseImpl implements GetHistoricalPricesUseCase {

    private final StockPriceRepository stockPriceRepository;

    public GetHistoricalPricesUseCaseImpl(StockPriceRepository stockPriceRepository) {
        this.stockPriceRepository = Objects.requireNonNull(stockPriceRepository, "stockPriceRepository must not be null");
    }

    @Override
    public Page<StockPrice> getHistoricalPrices(
            String ticker, TimeFrame timeFrame, LocalDate from, LocalDate to, Pageable pageable) {
        return stockPriceRepository.findHistoricalDataPaged(
                ticker.toUpperCase(), timeFrame, from, to, pageable);
    }
}
