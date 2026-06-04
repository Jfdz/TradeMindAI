package com.tradingsaas.marketdata.enrichment.adapter.out.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

final class FinnhubDtos {

    private FinnhubDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileDto(
            String ticker,
            String name,
            String logo,
            String country,
            String currency,
            String exchange,
            String ipo,
            @JsonProperty("marketCapitalization") Double marketCap,
            String phone,
            String weburl,
            @JsonProperty("finnhubIndustry") String industry) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NewsDto(
            long id,
            String headline,
            long datetime,
            String category,
            String source,
            String summary,
            String url,
            String image) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EarningsDto(
            @JsonProperty("symbol") String ticker,
            String period,
            int year,
            int quarter,
            @JsonProperty("actual") Double epsActual,
            @JsonProperty("estimate") Double epsEstimate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecommendationDto(
            @JsonProperty("symbol") String ticker,
            String period,
            int buy,
            int hold,
            int sell,
            int strongBuy,
            int strongSell) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PeersResponse(List<String> peers) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InsiderTransactionsDto(
            @JsonProperty("symbol") String ticker,
            List<InsiderTransactionDto> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InsiderTransactionDto(
            String name,
            // Signed share change for the filing: positive = acquired (buy),
            // negative = disposed (sell). Nullable when the provider omits it.
            @JsonProperty("change") Long change,
            @JsonProperty("transactionCode") String transactionCode,
            @JsonProperty("transactionDate") String transactionDate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SocialSentimentDto(
            @JsonProperty("symbol") String ticker,
            List<SocialSentimentEntryDto> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SocialSentimentEntryDto(
            // Integer mention counts only; the decimal *score* fields are
            // deliberately ignored (not validator-safe downstream).
            @JsonProperty("mention") Integer mention,
            @JsonProperty("positiveMention") Integer positiveMention,
            @JsonProperty("negativeMention") Integer negativeMention) {}
}
