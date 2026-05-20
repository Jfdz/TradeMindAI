package com.tradingsaas.tradingcore.adapter.in.web.dto;

import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import java.util.List;
import java.util.Map;

/**
 * First news item that grounded the AI reasoning, surfaced to the
 * user-facing signal feed for E1. Sourced from
 * {@code factsSnapshot.news[0]} on the {@link ReasoningArtifact}, which
 * ai-engine writes via {@code dataclasses.asdict} — meaning the JSONB
 * blob uses snake_case keys (e.g. {@code published_at}, {@code image}).
 *
 * <p>All fields are nullable so a missing image or unknown source does
 * not block the card from rendering the headline.
 */
public record ReasoningNewsSnapshot(
        String headline,
        String url,
        String imageUrl,
        String source,
        String publishedAt) {

    /**
     * Returns {@code null} when the artifact is missing, has no
     * {@code factsSnapshot}, or the snapshot's {@code news} array is
     * empty / malformed. Defensive by design — the user-facing card
     * just falls back to the no-image variant.
     */
    public static ReasoningNewsSnapshot fromArtifact(ReasoningArtifact artifact) {
        if (artifact == null) {
            return null;
        }
        Map<String, Object> snapshot = artifact.factsSnapshot();
        if (snapshot == null) {
            return null;
        }
        Object newsRaw = snapshot.get("news");
        if (!(newsRaw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object firstRaw = list.get(0);
        if (!(firstRaw instanceof Map<?, ?> first)) {
            return null;
        }
        String headline = stringOrNull(first.get("headline"));
        String url = stringOrNull(first.get("url"));
        // Skip when there's nothing meaningful to render.
        if (headline == null && url == null) {
            return null;
        }
        // Headline/url/source/publishedAt come from news[0] — the LLM's
        // grounded primary item. The image, however, is allowed to come
        // from the first news entry that actually carries one (typically
        // Finnhub when news[0] was Yahoo without a thumbnail). This keeps
        // the audit trail consistent while still surfacing a hero image
        // on the AI Decision Card whenever any grounded item has one.
        String imageUrl = firstNonBlankImage(list);
        return new ReasoningNewsSnapshot(
                headline,
                url,
                imageUrl,
                stringOrNull(first.get("source")),
                stringOrNull(first.get("published_at")));
    }

    private static String firstNonBlankImage(List<?> news) {
        for (Object raw : news) {
            if (!(raw instanceof Map<?, ?> item)) {
                continue;
            }
            String candidate = stringOrNull(item.get("image"));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
