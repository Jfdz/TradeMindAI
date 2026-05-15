package com.tradingsaas.tradingcore.adapter.in.web.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReasoningNewsSnapshotTest {

    private static ReasoningArtifact artifactWithNews(List<Map<String, Object>> news) {
        Map<String, Object> snapshot = Map.of(
                "schemaVersion", "v1.0",
                "ticker", "META",
                "news", news);
        return new ReasoningArtifact(
                "GENERATED", "anthropic_oauth", "claude-haiku-4-5", 0, null,
                snapshot, List.of(), List.of(), List.of(),
                Map.of("input_tokens", 100));
    }

    @Test
    void returnsNullWhenArtifactIsNull() {
        assertNull(ReasoningNewsSnapshot.fromArtifact(null));
    }

    @Test
    void returnsNullWhenFactsSnapshotIsNull() {
        ReasoningArtifact artifact = new ReasoningArtifact(
                "REFUSED_NO_FACTS", "stub", "stub-v1", 0,
                "context_outcome=NOT_TRACKED",
                null, List.of(), List.of(), List.of(), null);
        assertNull(ReasoningNewsSnapshot.fromArtifact(artifact));
    }

    @Test
    void returnsNullWhenNewsArrayIsEmpty() {
        assertNull(ReasoningNewsSnapshot.fromArtifact(artifactWithNews(List.of())));
    }

    @Test
    void returnsNullWhenNewsArrayIsMissingFromSnapshot() {
        Map<String, Object> snapshot = Map.of("schemaVersion", "v1.0", "ticker", "META");
        ReasoningArtifact artifact = new ReasoningArtifact(
                "GENERATED", "anthropic_oauth", "claude-haiku-4-5", 0, null,
                snapshot, List.of(), List.of(), List.of(), null);
        assertNull(ReasoningNewsSnapshot.fromArtifact(artifact));
    }

    @Test
    void extractsFirstNewsItemWithSnakeCaseKeys() {
        ReasoningArtifact artifact = artifactWithNews(List.of(
                Map.of(
                        "id", 42,
                        "headline", "META beats Q1 expectations",
                        "url", "https://reuters.com/x",
                        "image", "https://reuters.com/x.png",
                        "source", "Reuters",
                        "published_at", "2026-05-12T10:00:00Z"),
                // Second entry: must be ignored.
                Map.of(
                        "id", 43,
                        "headline", "Other headline",
                        "url", "https://other.example.com/")));

        ReasoningNewsSnapshot snapshot = ReasoningNewsSnapshot.fromArtifact(artifact);

        assertNotNull(snapshot);
        assertEquals("META beats Q1 expectations", snapshot.headline());
        assertEquals("https://reuters.com/x", snapshot.url());
        assertEquals("https://reuters.com/x.png", snapshot.imageUrl());
        assertEquals("Reuters", snapshot.source());
        assertEquals("2026-05-12T10:00:00Z", snapshot.publishedAt());
    }

    @Test
    void returnsSnapshotWhenImageIsMissing() {
        ReasoningArtifact artifact = artifactWithNews(List.of(Map.of(
                "id", 1,
                "headline", "Some headline",
                "url", "https://x.example.com/")));

        ReasoningNewsSnapshot snapshot = ReasoningNewsSnapshot.fromArtifact(artifact);

        assertNotNull(snapshot);
        assertEquals("Some headline", snapshot.headline());
        assertNull(snapshot.imageUrl());
        assertNull(snapshot.source());
    }

    @Test
    void returnsNullWhenBothHeadlineAndUrlAreMissing() {
        ReasoningArtifact artifact = artifactWithNews(List.of(Map.of(
                "id", 1, "image", "https://x.example.com/img.png")));
        assertNull(ReasoningNewsSnapshot.fromArtifact(artifact));
    }

    @Test
    void treatsBlankStringsAsNull() {
        ReasoningArtifact artifact = artifactWithNews(List.of(Map.of(
                "headline", "ok",
                "url", "https://example.com/x",
                "image", "   ",
                "source", "")));

        ReasoningNewsSnapshot snapshot = ReasoningNewsSnapshot.fromArtifact(artifact);

        assertNotNull(snapshot);
        assertEquals("ok", snapshot.headline());
        assertNull(snapshot.imageUrl());
        assertNull(snapshot.source());
    }

    @Test
    void tolersMalformedNewsEntries() {
        // First entry is a string instead of a Map → fromArtifact returns null
        // (defensive parse: bad shape should not blow up the user-facing card).
        Map<String, Object> snapshot = Map.of(
                "schemaVersion", "v1.0",
                "news", List.of("not-a-map"));
        ReasoningArtifact artifact = new ReasoningArtifact(
                "GENERATED", "stub", "stub-v1", 0, null,
                snapshot, List.of(), List.of(), List.of(), null);
        assertNull(ReasoningNewsSnapshot.fromArtifact(artifact));
    }

    @Test
    void borrowsImageFromLaterNewsItemWhenFirstHasNone() {
        // news[0] is the grounded primary (Yahoo, no image). news[1] is a
        // Finnhub item with a thumbnail. The snapshot should keep
        // news[0]'s headline/url/source/publishedAt but surface news[1]'s
        // image so the AI Decision Card still renders the hero.
        java.util.Map<String, Object> primary = new java.util.HashMap<>();
        primary.put("headline", "Primary grounded headline");
        primary.put("url", "https://yahoo.example.com/x");
        primary.put("source", "Yahoo Finance");
        primary.put("published_at", "2026-05-12T10:00:00Z");
        primary.put("image", null);

        ReasoningArtifact artifact = artifactWithNews(List.of(
                primary,
                Map.of(
                        "headline", "Other item",
                        "url", "https://finnhub.example.com/y",
                        "image", "https://img.finnhub.example.com/y.png",
                        "source", "Finnhub",
                        "published_at", "2026-05-12T09:00:00Z")));

        ReasoningNewsSnapshot snapshot = ReasoningNewsSnapshot.fromArtifact(artifact);

        assertNotNull(snapshot);
        assertEquals("Primary grounded headline", snapshot.headline());
        assertEquals("https://yahoo.example.com/x", snapshot.url());
        assertEquals("Yahoo Finance", snapshot.source());
        assertEquals("2026-05-12T10:00:00Z", snapshot.publishedAt());
        // Image borrowed from the second item.
        assertEquals("https://img.finnhub.example.com/y.png", snapshot.imageUrl());
    }

    @Test
    void returnsNullImageWhenNoNewsItemCarriesOne() {
        ReasoningArtifact artifact = artifactWithNews(List.of(
                Map.of("headline", "a", "url", "https://example.com/a"),
                Map.of("headline", "b", "url", "https://example.com/b")));

        ReasoningNewsSnapshot snapshot = ReasoningNewsSnapshot.fromArtifact(artifact);

        assertNotNull(snapshot);
        assertEquals("a", snapshot.headline());
        assertNull(snapshot.imageUrl());
    }
}
