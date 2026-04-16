package com.tradingsaas.marketdata.adapter.out.persistence.mapper;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.TechnicalIndicatorEntity;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(uses = SymbolEntityMapper.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TechnicalIndicatorEntityMapper {

    @Mapping(target = "id", ignore = true)
    TechnicalIndicatorEntity toEntity(TechnicalIndicator technicalIndicator);

    TechnicalIndicator toDomain(TechnicalIndicatorEntity entity);
}
