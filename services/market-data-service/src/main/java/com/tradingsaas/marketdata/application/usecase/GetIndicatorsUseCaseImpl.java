package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import com.tradingsaas.marketdata.domain.port.in.GetIndicatorsUseCase;
import com.tradingsaas.marketdata.domain.port.out.TechnicalIndicatorRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class GetIndicatorsUseCaseImpl implements GetIndicatorsUseCase {

    private final TechnicalIndicatorRepository repository;

    public GetIndicatorsUseCaseImpl(TechnicalIndicatorRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public List<TechnicalIndicator> getLatestIndicators(String ticker, List<TechnicalIndicatorType> types) {
        return repository.findLatestByTicker(ticker, types == null ? List.of() : types);
    }
}
