package com.tradingsaas.marketdata.enrichment.adapter.out.external;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tradingsaas.marketdata.enrichment.domain.exception.EnrichmentUnavailableException;
import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class YahooRssNewsAdapterTest {

    private static final String SAMPLE_RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Yahoo! Finance - AAPL</title>
                <item>
                  <title>Apple beats expectations in Q2</title>
                  <link>https://finance.yahoo.com/news/apple-beats-q2-123.html</link>
                  <pubDate>Mon, 12 May 2026 14:00:00 GMT</pubDate>
                  <description>Apple reported revenue above analyst estimates.</description>
                  <source>Reuters</source>
                </item>
                <item>
                  <title>iPhone sales remain steady</title>
                  <link>https://finance.yahoo.com/news/iphone-steady-456.html</link>
                  <pubDate>Sun, 11 May 2026 09:30:00 GMT</pubDate>
                  <description>Despite headwinds, sales hold up.</description>
                  <source>Bloomberg</source>
                </item>
                <item>
                  <title>Outside-window item, should be filtered</title>
                  <link>https://finance.yahoo.com/news/old-789.html</link>
                  <pubDate>Wed, 01 Jan 2020 00:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
            """;

    private WireMockServer wireMockServer;
    private YahooRssNewsAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
        adapter = new YahooRssNewsAdapter(
                WebClient.builder().baseUrl(wireMockServer.baseUrl()).build(),
                new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void providerNameAndPriorityMatchContract() {
        assertEquals("yahoo-rss", adapter.providerName());
        assertEquals(20, adapter.priority());
    }

    @Test
    void fetchTickerNewsParsesRssAndFiltersByWindow() {
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/rss+xml")
                        .withBody(SAMPLE_RSS)));

        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-31T00:00:00Z");
        List<NewsItem> items = adapter.fetchTickerNews("AAPL", from, to, 10);

        assertEquals(2, items.size(),
                "out-of-window item should be filtered out; got " + items.size());
        NewsItem first = items.get(0);
        assertEquals("Apple beats expectations in Q2", first.headline());
        assertEquals("https://finance.yahoo.com/news/apple-beats-q2-123.html", first.url());
        assertEquals("Reuters", first.source());
        assertEquals(Instant.parse("2026-05-12T14:00:00Z"), first.publishedAt());
        assertTrue(first.id() > 0, "stable id must be positive; got " + first.id());
    }

    @Test
    void fetchTickerNewsSortsByPublishedAtDescending() {
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse().withStatus(200).withBody(SAMPLE_RSS)));

        List<NewsItem> items = adapter.fetchTickerNews(
                "AAPL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                10);

        assertEquals(2, items.size());
        assertTrue(items.get(0).publishedAt().isAfter(items.get(1).publishedAt()),
                "items should be sorted desc by publishedAt");
    }

    @Test
    void fetchTickerNewsHonorsLimit() {
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse().withStatus(200).withBody(SAMPLE_RSS)));

        List<NewsItem> items = adapter.fetchTickerNews(
                "AAPL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                1);

        assertEquals(1, items.size());
    }

    @Test
    void fetchTickerNewsReturnsEmptyOnEmptyChannel() {
        String emptyRss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>empty</title></channel></rss>
                """;
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse().withStatus(200).withBody(emptyRss)));

        List<NewsItem> items = adapter.fetchTickerNews(
                "AAPL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                10);

        assertEquals(0, items.size());
    }

    @Test
    void fetchTickerNewsThrowsOnUpstream5xx() {
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(
                EnrichmentUnavailableException.class,
                () -> adapter.fetchTickerNews(
                        "AAPL",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T00:00:00Z"),
                        10));
    }

    @Test
    void fetchTickerNewsThrowsOnMalformedXml() {
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse().withStatus(200).withBody("<rss><not closed")));

        assertThrows(
                EnrichmentUnavailableException.class,
                () -> adapter.fetchTickerNews(
                        "AAPL",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T00:00:00Z"),
                        10));
    }

    @Test
    void fetchMarketNewsReturnsEmptyByDesign() {
        List<NewsItem> items = adapter.fetchMarketNews("general", 10);
        assertNotNull(items);
        assertEquals(0, items.size());
    }

    @Test
    void extractsImageFromMediaThumbnailAndPicksLargestWidth() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                  <channel>
                    <item>
                      <title>Thumbnail item</title>
                      <link>https://example.com/a</link>
                      <pubDate>Mon, 12 May 2026 14:00:00 GMT</pubDate>
                      <media:thumbnail url="https://img.example.com/small.jpg" width="80" height="60"/>
                      <media:thumbnail url="https://img.example.com/large.jpg" width="640" height="480"/>
                    </item>
                  </channel>
                </rss>
                """;
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse().withStatus(200).withBody(rss)));

        List<NewsItem> items = adapter.fetchTickerNews(
                "AAPL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                10);

        assertEquals(1, items.size());
        assertEquals("https://img.example.com/large.jpg", items.get(0).image());
    }

    @Test
    void extractsImageFromMediaContentWithImageMedium() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                  <channel>
                    <item>
                      <title>media:content item</title>
                      <link>https://example.com/b</link>
                      <pubDate>Mon, 12 May 2026 14:00:00 GMT</pubDate>
                      <media:content url="https://img.example.com/c.jpg" medium="image"/>
                    </item>
                  </channel>
                </rss>
                """;
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse().withStatus(200).withBody(rss)));

        List<NewsItem> items = adapter.fetchTickerNews(
                "AAPL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                10);

        assertEquals(1, items.size());
        assertEquals("https://img.example.com/c.jpg", items.get(0).image());
    }

    @Test
    void extractsImageFromEnclosureWithImageMimeType() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>enclosure item</title>
                      <link>https://example.com/c</link>
                      <pubDate>Mon, 12 May 2026 14:00:00 GMT</pubDate>
                      <enclosure url="https://img.example.com/d.jpg" type="image/jpeg" length="1024"/>
                    </item>
                  </channel>
                </rss>
                """;
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse().withStatus(200).withBody(rss)));

        List<NewsItem> items = adapter.fetchTickerNews(
                "AAPL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                10);

        assertEquals(1, items.size());
        assertEquals("https://img.example.com/d.jpg", items.get(0).image());
    }

    @Test
    void leavesImageNullWhenNoThumbnailElementsPresent() {
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse().withStatus(200).withBody(SAMPLE_RSS)));

        List<NewsItem> items = adapter.fetchTickerNews(
                "AAPL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                10);

        assertEquals(2, items.size());
        assertNull(items.get(0).image());
        assertNull(items.get(1).image());
    }

    @Test
    void ignoresEnclosureWithNonImageMimeType() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>audio enclosure</title>
                      <link>https://example.com/e</link>
                      <pubDate>Mon, 12 May 2026 14:00:00 GMT</pubDate>
                      <enclosure url="https://podcast.example.com/x.mp3" type="audio/mpeg" length="4096"/>
                    </item>
                  </channel>
                </rss>
                """;
        stubFor(get(urlPathEqualTo("/rss/2.0/headline"))
                .willReturn(aResponse().withStatus(200).withBody(rss)));

        List<NewsItem> items = adapter.fetchTickerNews(
                "AAPL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                10);

        assertEquals(1, items.size());
        assertNull(items.get(0).image());
    }
}
