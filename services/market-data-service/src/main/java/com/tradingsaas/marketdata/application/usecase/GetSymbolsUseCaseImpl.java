package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.port.in.GetSymbolsUseCase;
import com.tradingsaas.marketdata.domain.port.out.SymbolRepository;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class GetSymbolsUseCaseImpl implements GetSymbolsUseCase {

    private final SymbolRepository symbolRepository;

    public GetSymbolsUseCaseImpl(SymbolRepository symbolRepository) {
        this.symbolRepository = Objects.requireNonNull(symbolRepository, "symbolRepository must not be null");
    }

    @Override
    public Page<Symbol> getSymbols(Pageable pageable) {
        return symbolRepository.findAll(pageable);
    }
}
