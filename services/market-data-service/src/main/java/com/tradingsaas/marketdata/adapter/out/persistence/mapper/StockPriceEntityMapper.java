package com.tradingsaas.marketdata.adapter.out.persistence.mapper;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.StockPriceEntity;
import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = SymbolEntityMapper.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StockPriceEntityMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "open", source = "ohlcv.open")
    @Mapping(target = "high", source = "ohlcv.high")
    @Mapping(target = "low", source = "ohlcv.low")
    @Mapping(target = "close", source = "ohlcv.close")
    @Mapping(target = "volume", source = "ohlcv.volume")
    @Mapping(target = "adjustedClose", source = "adjustedClose")
    StockPriceEntity toEntity(StockPrice stockPrice);

    @Mapping(target = "ohlcv", expression = "java(toOhlcv(entity))")
    @Mapping(target = "adjustedClose", source = "adjustedClose")
    StockPrice toDomain(StockPriceEntity entity);

    default OHLCV toOhlcv(StockPriceEntity entity) {
        return new OHLCV(
                entity.getOpen(),
                entity.getHigh(),
                entity.getLow(),
                entity.getClose(),
                entity.getVolume());
    }
}
