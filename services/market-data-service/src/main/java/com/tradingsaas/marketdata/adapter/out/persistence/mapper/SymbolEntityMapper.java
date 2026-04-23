package com.tradingsaas.marketdata.adapter.out.persistence.mapper;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.SymbolEntity;
import com.tradingsaas.marketdata.domain.model.Symbol;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SymbolEntityMapper {

    Symbol toDomain(SymbolEntity entity);

    SymbolEntity toEntity(Symbol symbol);
}
