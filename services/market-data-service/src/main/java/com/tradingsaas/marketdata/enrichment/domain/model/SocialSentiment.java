package com.tradingsaas.marketdata.enrichment.domain.model;

import java.util.Objects;

/**
 * Aggregated recent social-media sentiment for a ticker (Fase 4 enrichment).
 *
 * <p>Deliberately exposed as integer MENTION COUNTS, not decimal scores: the
 * downstream ai-engine reasoning validator grounds every decimal token against
 * price facts and would reject a score like {@code 0.62} as an ungrounded
 * number. Counts ({@code positiveMentions} / {@code negativeMentions} /
 * {@code totalMentions}) are validator-safe to cite verbatim, like
 * {@link InsiderActivity}.
 */
public record SocialSentiment(
        String ticker, int positiveMentions, int negativeMentions, int totalMentions) {

    public SocialSentiment {
        Objects.requireNonNull(ticker, "ticker must not be null");
    }

    public boolean hasActivity() {
        return totalMentions > 0;
    }
}
