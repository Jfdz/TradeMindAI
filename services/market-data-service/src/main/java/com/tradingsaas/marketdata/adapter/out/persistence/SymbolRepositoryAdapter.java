package com.tradingsaas.marketdata.adapter.out.persistence;

import com.tradingsaas.marketdata.adapter.out.persistence.mapper.SymbolEntityMapper;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.port.out.SymbolRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SymbolRepositoryAdapter implements SymbolRepository {

    private final SymbolJpaRepository jpaRepository;
    private final SymbolEntityMapper mapper;

    public SymbolRepositoryAdapter(SymbolJpaRepository jpaRepository, SymbolEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Symbol> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Symbol> findByTicker(String ticker) {
        return jpaRepository.findById(ticker.toUpperCase()).map(mapper::toDomain);
    }
}
