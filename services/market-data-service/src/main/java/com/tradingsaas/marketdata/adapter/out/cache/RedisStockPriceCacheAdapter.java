package com.tradingsaas.marketdata.adapter.out.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.StockPriceCache;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisStockPriceCacheAdapter implements StockPriceCache {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "market-data:price:latest:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisStockPriceCacheAdapter(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void cacheLatest(StockPrice price) {
        String key = key(price.symbol().ticker(), price.timeFrame());
        Map<String, Object> value = new HashMap<>();
        value.put("ticker", price.symbol().ticker());
        value.put("name", price.symbol().name());
        value.put("exchange", price.symbol().exchange());
        value.put("sector", price.symbol().sector());
        value.put("active", price.symbol().active());
        value.put("date", price.date().toString());
        value.put("timeFrame", price.timeFrame().name());
        value.put("open", price.ohlcv().open().toPlainString());
        value.put("high", price.ohlcv().high().toPlainString());
        value.put("low", price.ohlcv().low().toPlainString());
        value.put("close", price.ohlcv().close().toPlainString());
        value.put("volume", price.ohlcv().volume());
        value.put("adjustedClose", price.adjustedClose().toPlainString());
        redisTemplate.opsForValue().set(key, value, TTL);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<StockPrice> findLatest(String ticker, TimeFrame timeFrame) {
        Object raw = redisTemplate.opsForValue().get(key(ticker, timeFrame));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> value = objectMapper.convertValue(raw, Map.class);
            Symbol symbol = new Symbol(
                    (String) value.get("ticker"),
                    (String) value.get("name"),
                    (String) value.get("exchange"),
                    (String) value.get("sector"),
                    (Boolean) value.get("active"));
            OHLCV ohlcv = new OHLCV(
                    new BigDecimal((String) value.get("open")),
                    new BigDecimal((String) value.get("high")),
                    new BigDecimal((String) value.get("low")),
                    new BigDecimal((String) value.get("close")),
                    ((Number) value.get("volume")).longValue());
            return Optional.of(new StockPrice(
                    symbol,
                    LocalDate.parse((String) value.get("date")),
                    TimeFrame.valueOf((String) value.get("timeFrame")),
                    ohlcv,
                    new BigDecimal((String) value.get("adjustedClose"))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void evict(String ticker, TimeFrame timeFrame) {
        redisTemplate.delete(key(ticker, timeFrame));
    }

    private static String key(String ticker, TimeFrame timeFrame) {
        return KEY_PREFIX + ticker.toUpperCase() + ":" + timeFrame.name();
    }
}
