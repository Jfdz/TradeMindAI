package com.tradingsaas.tradingcore.domain.exception;

public class RateLimitExceededException extends RuntimeException {

    private final long limit;
    private final long resetEpochSecond;

    public RateLimitExceededException(long limit, long resetEpochSecond) {
        super("Rate limit of " + limit + " requests/day exceeded");
        this.limit = limit;
        this.resetEpochSecond = resetEpochSecond;
    }

    public long getLimit() { return limit; }
    public long getResetEpochSecond() { return resetEpochSecond; }
}
